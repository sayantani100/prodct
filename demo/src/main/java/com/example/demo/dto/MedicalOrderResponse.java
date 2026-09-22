package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicalOrderResponse {

    @JsonProperty("Lines")
    private List<String> lines;

    @JsonProperty("Comprehension")
    private ComprehensionData comprehension;

    @JsonProperty("RecognitionEngine")
    private String recognitionEngine;

    public List<String> getLines() { return lines; }
    public void setLines(List<String> lines) { this.lines = lines; }

    public ComprehensionData getComprehension() { return comprehension; }
    public void setComprehension(ComprehensionData comprehension) { this.comprehension = comprehension; }

    public String getRecognitionEngine() { return recognitionEngine; }
    public void setRecognitionEngine(String recognitionEngine) { this.recognitionEngine = recognitionEngine; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComprehensionData {
        @JsonProperty("document_classification")
        private String document_classification;
        @JsonProperty("order_required")
        private boolean order_required;
        @JsonProperty("order_action_plan")
        private String order_action_plan;
        @JsonProperty("clean_transcript")
        private String clean_transcript;
        @JsonProperty("mediclaim")
        private Mediclaim mediclaim;

        public String getDocument_classification() { return document_classification; }
        public void setDocument_classification(String v) { this.document_classification = v; }
        public boolean isOrder_required() { return order_required; }
        public void setOrder_required(boolean v) { this.order_required = v; }
        public String getOrder_action_plan() { return order_action_plan; }
        public void setOrder_action_plan(String v) { this.order_action_plan = v; }
        public String getClean_transcript() { return clean_transcript; }
        public void setClean_transcript(String v) { this.clean_transcript = v; }
        public Mediclaim getMediclaim() { return mediclaim; }
        public void setMediclaim(Mediclaim v) { this.mediclaim = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Mediclaim {
        private Patient patient;
        @JsonProperty("emergency_contact")
        private EmergencyContact emergencyContact;
        private List<Insurance> insurance;

        public Patient getPatient() { return patient; }
        public void setPatient(Patient v) { this.patient = v; }
        public EmergencyContact getEmergencyContact() { return emergencyContact; }
        public void setEmergencyContact(EmergencyContact v) { this.emergencyContact = v; }
        public List<Insurance> getInsurance() { return insurance; }
        public void setInsurance(List<Insurance> v) { this.insurance = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Patient {
        @JsonProperty("permanent_address")
        private AddressBlock permanentAddress;
        @JsonProperty("temporary_address")
        private AddressBlock temporaryAddress;

        public AddressBlock getPermanentAddress() { return permanentAddress; }
        public void setPermanentAddress(AddressBlock v) { this.permanentAddress = v; }
        public AddressBlock getTemporaryAddress() { return temporaryAddress; }
        public void setTemporaryAddress(AddressBlock v) { this.temporaryAddress = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddressBlock {
        @JsonProperty("same_as_permanent")
        private Boolean sameAsPermanent;
        @JsonProperty("patient_name")
        private String patientName;
        private String address;
        private String location;
        @JsonProperty("phone_number")
        private String phoneNumber;
        private String email;

        public Boolean getSameAsPermanent() { return sameAsPermanent; }
        public void setSameAsPermanent(Boolean v) { this.sameAsPermanent = v; }
        public String getPatientName() { return patientName; }
        public void setPatientName(String v) { this.patientName = v; }
        public String getAddress() { return address; }
        public void setAddress(String v) { this.address = v; }
        public String getLocation() { return location; }
        public void setLocation(String v) { this.location = v; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String v) { this.phoneNumber = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmergencyContact {
        private String name;
        private String address;
        private String location;
        @JsonProperty("phone_number")
        private String phoneNumber;
        @JsonProperty("relation_to_patient")
        private String relationToPatient;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getAddress() { return address; }
        public void setAddress(String v) { this.address = v; }
        public String getLocation() { return location; }
        public void setLocation(String v) { this.location = v; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String v) { this.phoneNumber = v; }
        public String getRelationToPatient() { return relationToPatient; }
        public void setRelationToPatient(String v) { this.relationToPatient = v; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Insurance {
        @JsonProperty("insurance_name")
        private String insuranceName;
        private String location;
        @JsonProperty("unique_number")
        private String uniqueNumber;
        private String type;
        @JsonProperty("ssn_id")
        private String ssnId;

        public String getInsuranceName() { return insuranceName; }
        public void setInsuranceName(String v) { this.insuranceName = v; }
        public String getLocation() { return location; }
        public void setLocation(String v) { this.location = v; }
        public String getUniqueNumber() { return uniqueNumber; }
        public void setUniqueNumber(String v) { this.uniqueNumber = v; }
        public String getType() { return type; }
        public void setType(String v) { this.type = v; }
        public String getSsnId() { return ssnId; }
        public void setSsnId(String v) { this.ssnId = v; }
    }
}