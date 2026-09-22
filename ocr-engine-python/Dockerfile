# Use the stable verified official PaddlePaddle linux base image
FROM paddlepaddle/paddle:2.6.1

# Install system graphics, OpenMP, and document parsing dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    poppler-utils \
    libgomp1 \
    libxrender1 \
    libxext6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .

# FIX: The paddlepaddle base image preconfigures pip (via its own pip.conf)
# to pull from a regional mirror. That mirror was serving a package build
# whose bytes don't match PyPI's recorded hash for it (mdurl / more-itertools /
# encutils -- transitive deps of transformers), which pip correctly rejects
# as a possible corruption/tamper rather than silently installing it.
# Forcing the official PyPI index here overrides that inherited mirror config
# for this install only, without needing to touch the base image itself.
ENV PIP_INDEX_URL=https://pypi.org/simple
ENV PIP_EXTRA_INDEX_URL=

# Install CPU-only torch FIRST, from PyTorch's own CPU wheel index. Without
# this, `pip install torch` (as part of requirements.txt) pulls the default
# GPU/CUDA build -- torch itself plus nvidia_cudnn add up to ~900MB of
# downloads that are completely unusable here anyway, since this container
# has no GPU. The CPU-only wheel is roughly 200MB instead.
RUN pip install --no-cache-dir --default-timeout=1000 \
    --index-url https://download.pytorch.org/whl/cpu \
    torch==2.3.0

# Increased timeout ceiling to tolerate slower direct-PyPI downloads for
# the remaining wheels now that we're not using a regional mirror.
# torch is already satisfied by the CPU-only install above, so pip will
# skip re-downloading it here as long as requirements.txt's constraint
# (torch not pinned to a conflicting exact version) is compatible.
RUN pip install --no-cache-dir --default-timeout=1000 -r requirements.txt

COPY main.py .

EXPOSE 8000

# Wipe out corrupted PaddleOCR download folders before starting, but only
# when explicitly requested -- otherwise this runs on every restart and
# defeats the persistent paddleocr_cache volume in docker-compose.yml,
# forcing a full re-download of the detection models every time.
CMD sh -c '[ "$CLEAR_PADDLEOCR_CACHE" = "1" ] && rm -rf /root/.paddleocr; uvicorn main:app --host 0.0.0.0 --port 8000'