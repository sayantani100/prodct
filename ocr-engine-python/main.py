from fastapi import FastAPI, File, UploadFile, Header, HTTPException
from pdf2image import convert_from_bytes
from PIL import Image
from paddleocr import PaddleOCR
from transformers import TrOCRProcessor, VisionEncoderDecoderModel
import torch
import numpy as np
import cv2
import io
import requests
import json
import re
import os
import shutil
import time
import threading
import traceback

app = FastAPI()

ocr = None
try:
    ocr = PaddleOCR(
        use_angle_cls=True,
        lang='en',
        show_log=False,
        det_db_thresh=0.2,
        det_db_box_thresh=0.2,
        det_db_unclip_ratio=2.0
    )
except Exception as boot_error:
    print(f"[SYSTEM RESET]: Detected corrupted download file: {str(boot_error)}")
    broken_cache_path = os.path.expanduser("~/.paddleocr")
    if os.path.exists(broken_cache_path):
        try:
            shutil.rmtree(broken_cache_path)
        except Exception:
            pass
    ocr = PaddleOCR(use_angle_cls=True, lang='en', show_log=False)

TROCR_MODEL_NAME = "microsoft/trocr-base-handwritten"
TROCR_LOAD_MAX_RETRIES = 2
TROCR_LOAD_RETRY_BACKOFF_SECONDS = 10

trocr_processor = None
trocr_model = None
trocr_load_error = None
trocr_device = "cuda" if torch.cuda.is_available() else "cpu"


def _load_trocr():
    global trocr_processor, trocr_model, trocr_load_error
    for attempt in range(1, TROCR_LOAD_MAX_RETRIES + 2):
        try:
            print(f"[TROCR]: Loading {TROCR_MODEL_NAME} (attempt {attempt}) on {trocr_device}...")
            processor = TrOCRProcessor.from_pretrained(TROCR_MODEL_NAME)
            model = VisionEncoderDecoderModel.from_pretrained(TROCR_MODEL_NAME)
            model.to(trocr_device)
            model.eval()
            trocr_processor = processor
            trocr_model = model
            trocr_load_error = None
            print(f"[TROCR]: Loaded {TROCR_MODEL_NAME} successfully on {trocr_device}.")
            return
        except Exception as trocr_boot_error:
            trocr_load_error = str(trocr_boot_error)
            print(f"[TROCR]: Load attempt {attempt} failed: {trocr_load_error}")
            print(traceback.format_exc())
            if attempt <= TROCR_LOAD_MAX_RETRIES:
                time.sleep(TROCR_LOAD_RETRY_BACKOFF_SECONDS)
    print(f"[TROCR]: All load attempts failed. Falling back to PaddleOCR recognition.")


_load_trocr()

OLLAMA_GENERATE_URL = "http://ollama:11434/api/generate"
OLLAMA_MODEL = "phi3"

# Sized for the mediclaim structured schema. On CPU, Phi-3 generates at
# roughly 5-6 tokens/second -- num_predict=900 realistically takes
# 150-180s for generation alone, before prompt processing and OCR. This
# timeout must stay comfortably above that, and TrOcrService.java's
# timeout (and application.properties' async timeout) must in turn stay
# comfortably above THIS value.
OLLAMA_REQUEST_TIMEOUT = 300
OLLAMA_MAX_RETRIES = 1  # a retry here costs several more minutes -- keep this low
OLLAMA_RETRY_BACKOFF_SECONDS = 5

MIN_RECOGNITION_CONFIDENCE = 0.5
MIN_IMAGE_DIMENSION_PX = 1500
TROCR_BATCH_SIZE = 8
LINE_CROP_PADDING_PX = 4
TROCR_NUM_BEAMS = 2  # trimmed from 4 to reduce recognition time on CPU

MIN_CHARS_FOR_ANALYSIS = 12
MIN_ALPHA_CHARS_FOR_ANALYSIS = 6

OCR_ENGINE_SHARED_SECRET = os.environ.get("OCR_ENGINE_SHARED_SECRET")

MAX_UPLOAD_SIZE_BYTES = 15 * 1024 * 1024
ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".pdf", ".tif", ".tiff"}

NIL_VALUE = "nil"

# Empty skeleton for the mediclaim fields. Used both to shape the prompt's
# expected output and as the anti-fabrication backstop: any leaf value that
# isn't a genuine non-empty string from the model gets collapsed back to
# this skeleton's "nil" default. The prompt asking the model to say "nil"
# is not, by itself, trusted -- this merge step enforces it in code.
MEDICLAIM_SKELETON = {
    "patient": {
        "permanent_address": {
            "patient_name": NIL_VALUE, "address": NIL_VALUE, "location": NIL_VALUE,
            "phone_number": NIL_VALUE, "email": NIL_VALUE
        },
        "temporary_address": {
            "same_as_permanent": False,
            "patient_name": NIL_VALUE, "address": NIL_VALUE, "location": NIL_VALUE,
            "phone_number": NIL_VALUE, "email": NIL_VALUE
        }
    },
    "emergency_contact": {
        "name": NIL_VALUE, "address": NIL_VALUE, "location": NIL_VALUE,
        "phone_number": NIL_VALUE, "relation_to_patient": NIL_VALUE
    },
    "insurance": [
        {"insurance_name": NIL_VALUE, "location": NIL_VALUE, "unique_number": NIL_VALUE, "type": NIL_VALUE, "ssn_id": NIL_VALUE},
        {"insurance_name": NIL_VALUE, "location": NIL_VALUE, "unique_number": NIL_VALUE, "type": NIL_VALUE, "ssn_id": NIL_VALUE},
        {"insurance_name": NIL_VALUE, "location": NIL_VALUE, "unique_number": NIL_VALUE, "type": NIL_VALUE, "ssn_id": NIL_VALUE}
    ]
}


def _warm_up_ollama():
    def _run():
        try:
            requests.post(
                OLLAMA_GENERATE_URL,
                json={"model": OLLAMA_MODEL, "prompt": "warm up", "stream": False, "options": {"num_predict": 1}},
                timeout=300
            )
            print("[OLLAMA WARM-UP]: phi3 loaded and ready.")
        except Exception as warm_up_error:
            print(f"[OLLAMA WARM-UP]: failed to pre-warm model: {str(warm_up_error)}")

    threading.Thread(target=_run, daemon=True).start()


@app.on_event("startup")
async def on_startup():
    if not OCR_ENGINE_SHARED_SECRET:
        print("[SECURITY WARNING]: OCR_ENGINE_SHARED_SECRET is not set.")
    _warm_up_ollama()


@app.get("/health")
async def health():
    return {
        "paddleocr_loaded": ocr is not None,
        "trocr_loaded": trocr_processor is not None and trocr_model is not None,
        "trocr_device": trocr_device,
        "active_recognizer": "trocr" if (trocr_processor is not None and trocr_model is not None) else "paddleocr_fallback",
        "shared_secret_configured": OCR_ENGINE_SHARED_SECRET is not None
    }


def _has_sufficient_content(text):
    stripped = text.strip()
    if len(stripped) < MIN_CHARS_FOR_ANALYSIS:
        return False
    alpha_count = sum(1 for c in stripped if c.isalpha())
    if alpha_count < MIN_ALPHA_CHARS_FOR_ANALYSIS:
        return False
    return True


def _deep_merge_with_skeleton(skeleton, value):
    """
    Anti-fabrication enforcement: recursively fills any missing/malformed
    keys from Phi-3's output with the skeleton's "nil" defaults. Any leaf
    that isn't a genuinely non-empty string collapses back to "nil" --
    blank/null/missing/whitespace-only all render identically as "nil",
    regardless of what the model actually returned for that field.
    """
    if isinstance(skeleton, dict):
        if not isinstance(value, dict):
            return skeleton
        merged = {}
        for key, default in skeleton.items():
            merged[key] = _deep_merge_with_skeleton(default, value.get(key))
        return merged
    if isinstance(skeleton, list):
        if not isinstance(value, list):
            return skeleton
        merged = []
        for i, default_item in enumerate(skeleton):
            item_value = value[i] if i < len(value) else None
            merged.append(_deep_merge_with_skeleton(default_item, item_value))
        return merged
    if isinstance(value, str) and value.strip() and value.strip().lower() not in ("null", "none", ""):
        return value.strip()
    return skeleton


def analyze_any_document_intent(raw_text):
    system_prompt = (
        "You are an advanced cognitive workflow routing assistant. Analyze the following OCR text extracted "
        "from an uploaded medical document and extract structured information.\n\n"
        "CRITICAL RULE ON MISSING INFORMATION: For every single field below, use the exact string \"nil\" if "
        "that information does not literally, explicitly appear in the source text. Do NOT guess, infer, "
        "estimate, or invent a plausible-sounding value for any field. It is far better to return \"nil\" than "
        "to fabricate a name, number, address, or ID that is not clearly present in the text. This applies "
        "especially to insurance numbers and SSN/ID fields -- never invent a number.\n\n"
        "Return ONLY a valid JSON object with exactly this structure (all fields required, using \"nil\" for "
        "anything not found):\n"
        "{\n"
        "  \"document_classification\": \"short label for the document type\",\n"
        "  \"order_required\": true or false (true only if the text contains an actionable request, signed "
        "statement, or medical command requiring processing),\n"
        "  \"order_action_plan\": \"one sentence on what action is needed, or 'No action required'\",\n"
        "  \"clean_transcript\": \"a polished transcription fixing only OBVIOUS OCR typos -- never invent "
        "content not literally present; mark illegible words as [unclear]\",\n"
        "  \"mediclaim\": {\n"
        "    \"patient\": {\n"
        "      \"permanent_address\": {\"patient_name\": \"\", \"address\": \"\", \"location\": \"\", "
        "\"phone_number\": \"\", \"email\": \"\"},\n"
        "      \"temporary_address\": {\"same_as_permanent\": true or false, \"patient_name\": \"\", "
        "\"address\": \"\", \"location\": \"\", \"phone_number\": \"\", \"email\": \"\"}\n"
        "    },\n"
        "    \"emergency_contact\": {\"name\": \"\", \"address\": \"\", \"location\": \"\", "
        "\"phone_number\": \"\", \"relation_to_patient\": \"\"},\n"
        "    \"insurance\": [\n"
        "      {\"insurance_name\": \"\", \"location\": \"\", \"unique_number\": \"\", \"type\": \"\", \"ssn_id\": \"\"},\n"
        "      {\"insurance_name\": \"\", \"location\": \"\", \"unique_number\": \"\", \"type\": \"\", \"ssn_id\": \"\"},\n"
        "      {\"insurance_name\": \"\", \"location\": \"\", \"unique_number\": \"\", \"type\": \"\", \"ssn_id\": \"\"}\n"
        "    ]\n"
        "  }\n"
        "}\n\n"
        "Do not include any conversational preamble, notes, or markdown backticks outside of the raw valid JSON payload."
    )

    combined_prompt = f"System Rules:\n{system_prompt}\n\nDocument Text to Parse:\n\"\"\"\n{raw_text}\n\"\"\""

    payload = {
        "model": OLLAMA_MODEL,
        "prompt": combined_prompt,
        "stream": False,
        "format": "json",
        "options": {"temperature": 0.1, "num_predict": 900}
    }

    last_error = None
    for attempt in range(1, OLLAMA_MAX_RETRIES + 2):
        try:
            response = requests.post(OLLAMA_GENERATE_URL, json=payload, timeout=OLLAMA_REQUEST_TIMEOUT)
            if response.status_code == 200:
                result_json = response.json()
                assistant_content = result_json.get("response", "{}").strip()

                start_idx = assistant_content.find('{')
                end_idx = assistant_content.rfind('}')
                cleaned_payload_string = assistant_content[start_idx:end_idx + 1] if start_idx != -1 and end_idx != -1 else assistant_content

                try:
                    parsed = json.loads(cleaned_payload_string, strict=False)
                except Exception as parse_error:
                    print(f"[OLLAMA JSON PARSE FAILED]: {str(parse_error)} (response length: {len(assistant_content)} chars)")
                    return {
                        "document_classification": "Unclassified Document",
                        "order_required": False,
                        "order_action_plan": "No action required",
                        "clean_transcript": raw_text.replace("\n", " ").strip(),
                        "mediclaim": MEDICLAIM_SKELETON,
                        "_warning": "Phi-3 JSON response could not be parsed; mediclaim fields default to nil."
                    }

                merged_mediclaim = _deep_merge_with_skeleton(MEDICLAIM_SKELETON, parsed.get("mediclaim"))
                return {
                    "document_classification": parsed.get("document_classification") or "Unclassified Document",
                    "order_required": bool(parsed.get("order_required", False)),
                    "order_action_plan": parsed.get("order_action_plan") or "No action required",
                    "clean_transcript": parsed.get("clean_transcript") or raw_text.replace("\n", " ").strip(),
                    "mediclaim": merged_mediclaim
                }
            else:
                last_error = f"Ollama node error code: {response.status_code}"
        except requests.exceptions.Timeout:
            last_error = f"Ollama request timed out after {OLLAMA_REQUEST_TIMEOUT}s (attempt {attempt})"
        except requests.exceptions.ConnectionError:
            last_error = f"Ollama connection error (attempt {attempt})"
        except Exception:
            last_error = f"Local AI core fault (attempt {attempt})"

        if attempt <= OLLAMA_MAX_RETRIES:
            time.sleep(OLLAMA_RETRY_BACKOFF_SECONDS)

    return {"error": last_error or "Ollama request failed after retries."}


def _preprocess_for_detection(image_np):
    gray = cv2.cvtColor(image_np, cv2.COLOR_BGR2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, h=10)
    adaptive = cv2.adaptiveThreshold(
        denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, blockSize=25, C=15
    )
    sharpen_kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
    sharpened_gray = cv2.filter2D(adaptive, -1, sharpen_kernel)
    return cv2.cvtColor(sharpened_gray, cv2.COLOR_GRAY2BGR)


def _upscale_if_small(image_np):
    h, w = image_np.shape[:2]
    shorter_side = min(h, w)
    if 0 < shorter_side < MIN_IMAGE_DIMENSION_PX:
        scale = MIN_IMAGE_DIMENSION_PX / shorter_side
        return cv2.resize(image_np, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    return image_np


def _detect_line_boxes(preprocessed_bgr):
    result = ocr.ocr(preprocessed_bgr, det=True, rec=False, cls=False)
    if result is None or len(result) == 0 or result[0] is None:
        return []
    boxes = []
    for box_coords in result[0]:
        try:
            pts = np.array(box_coords, dtype=np.float32).reshape(-1, 2)
            x_min = int(np.min(pts[:, 0])); x_max = int(np.max(pts[:, 0]))
            y_min = int(np.min(pts[:, 1])); y_max = int(np.max(pts[:, 1]))
            boxes.append([x_min, y_min, x_max, y_max])
        except Exception:
            continue
    boxes.sort(key=lambda b: b[1])
    return boxes


def _recognize_crops_with_trocr(original_bgr, boxes):
    h, w = original_bgr.shape[:2]
    crops = []
    valid_boxes = []
    for box in boxes:
        x_min, y_min, x_max, y_max = box
        x_min = max(0, x_min - LINE_CROP_PADDING_PX)
        y_min = max(0, y_min - LINE_CROP_PADDING_PX)
        x_max = min(w, x_max + LINE_CROP_PADDING_PX)
        y_max = min(h, y_max + LINE_CROP_PADDING_PX)
        if x_max <= x_min or y_max <= y_min:
            continue
        crop = original_bgr[y_min:y_max, x_min:x_max]
        if crop.size == 0:
            continue
        crop_rgb = cv2.cvtColor(crop, cv2.COLOR_BGR2RGB)
        crops.append(Image.fromarray(crop_rgb))
        valid_boxes.append(box)

    if not crops:
        return []

    results = []
    with torch.no_grad():
        for i in range(0, len(crops), TROCR_BATCH_SIZE):
            batch_crops = crops[i:i + TROCR_BATCH_SIZE]
            batch_boxes = valid_boxes[i:i + TROCR_BATCH_SIZE]
            pixel_values = trocr_processor(images=batch_crops, return_tensors="pt").pixel_values.to(trocr_device)
            generated_ids = trocr_model.generate(pixel_values, max_length=64, num_beams=TROCR_NUM_BEAMS)
            texts = trocr_processor.batch_decode(generated_ids, skip_special_tokens=True)
            for box, text in zip(batch_boxes, texts):
                cleaned = text.strip()
                if cleaned:
                    results.append((box, cleaned))
    return results


def _recognize_with_paddleocr_fallback(preprocessed_bgr):
    result = ocr.ocr(preprocessed_bgr, cls=True)
    if result is None or len(result) == 0 or result[0] is None:
        return []
    line_items = []
    for line in result[0]:
        if line is None or len(line) != 2:
            continue
        try:
            box_coords, text_tuple = line
            text_string, confidence_score = text_tuple
            if float(confidence_score) < MIN_RECOGNITION_CONFIDENCE:
                continue
            pts = np.array(box_coords, dtype=np.float32).reshape(-1, 2)
            x_min = int(np.min(pts[:, 0])); x_max = int(np.max(pts[:, 0]))
            y_min = int(np.min(pts[:, 1])); y_max = int(np.max(pts[:, 1]))
            line_items.append(([x_min, y_min, x_max, y_max], str(text_string).strip()))
        except Exception:
            continue
    line_items.sort(key=lambda item: item[0][1])
    return line_items


def _group_into_reading_lines(box_text_items):
    items = list(box_text_items)
    output_lines = []
    while len(items) > 0:
        current_box, current_text = items.pop(0)
        row_items = [(current_box, current_text)]
        box_height = current_box[3] - current_box[1]
        remains = []
        for box_b, text_b in items:
            overlap = min(current_box[3], box_b[3]) - max(current_box[1], box_b[1])
            if box_height > 0 and overlap > 0.40 * box_height:
                row_items.append((box_b, text_b))
            else:
                remains.append((box_b, text_b))
        items = remains
        row_items.sort(key=lambda x: x[0][0])
        sentence_line = " ".join([t for _, t in row_items]).strip()
        if sentence_line:
            output_lines.append(sentence_line)
    return output_lines


@app.post("/predict")
async def predict(
        file: UploadFile = File(...),
        x_engine_secret: str = Header(default=None)
):
    if OCR_ENGINE_SHARED_SECRET:
        if not x_engine_secret or x_engine_secret != OCR_ENGINE_SHARED_SECRET:
            raise HTTPException(status_code=401, detail="Unauthorized")

    filename = (file.filename or "").lower()
    ext = os.path.splitext(filename)[1]
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="Unsupported file type.")

    try:
        file_bytes = await file.read()

        if len(file_bytes) > MAX_UPLOAD_SIZE_BYTES:
            raise HTTPException(status_code=413, detail="File too large.")

        images_to_process = []
        if filename.endswith('.pdf'):
            pdf_pages = convert_from_bytes(file_bytes)
            for page in pdf_pages:
                opencv_img = cv2.cvtColor(np.array(page), cv2.COLOR_RGB2BGR)
                images_to_process.append(opencv_img)
        else:
            np_arr = np.frombuffer(file_bytes, np.uint8)
            opencv_img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
            if opencv_img is not None:
                images_to_process.append(opencv_img)

        raw_lines = []
        trocr_available = trocr_processor is not None and trocr_model is not None

        for image_np in images_to_process:
            if image_np is None:
                continue
            upscaled = _upscale_if_small(image_np)
            preprocessed = _preprocess_for_detection(upscaled)

            if trocr_available:
                boxes = _detect_line_boxes(preprocessed)
                if not boxes:
                    continue
                line_items = _recognize_crops_with_trocr(upscaled, boxes)
            else:
                line_items = _recognize_with_paddleocr_fallback(preprocessed)

            if not line_items:
                continue
            raw_lines.extend(_group_into_reading_lines(line_items))

        combined_text = "\n".join(raw_lines).strip()

        if not combined_text:
            return {"error": "No meaningful text layout could be extracted from this image file."}

        if not _has_sufficient_content(combined_text):
            return {
                "Lines": raw_lines,
                "error": "Insufficient legible text was detected to analyze this document reliably. "
                         "No automated classification was attempted -- manual review is required.",
                "RecognitionEngine": "trocr" if trocr_available else "paddleocr_fallback"
            }

        analysis_result = analyze_any_document_intent(combined_text)

        return {
            "Lines": raw_lines,
            "Comprehension": analysis_result,
            "RecognitionEngine": "trocr" if trocr_available else "paddleocr_fallback"
        }

    except HTTPException:
        raise
    except Exception as general_endpoint_error:
        print(f"[PREDICT ERROR]: {type(general_endpoint_error).__name__}")
        return {"error": "Internal processing error."}