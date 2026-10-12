package com.emergency.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class EmergencyDispatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmergencyDispatchApplication.class, args);
    }
}

// ==========================================
// REST CONTROLLERS
// ==========================================
@RestController
@RequestMapping("/api/v1")
class EmergencyController {

    // In-memory thread-safe databases for demonstration purposes
    private final Map<String, EmergencyRequest> emergencyStore = new ConcurrentHashMap<>();
    private final Map<String, Ambulance> ambulanceStore = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(1000);

    public EmergencyController() {
        // Seed default sample ambulances for testing
        ambulanceStore.put("amb_001", new Ambulance("amb_001", "John Driver", "+1987654321", "ICU", "AVAILABLE", 12.9716, 77.5946));
        ambulanceStore.put("amb_002", new Ambulance("amb_002", "Jane Smith", "+1122334455", "BASIC", "AVAILABLE", 12.9352, 77.6245));
    }

    /**
     * Task 1 & 3: Request Ambulance API
     * POST /api/v1/emergencies
     */
    @PostMapping("/emergencies")
    public ResponseEntity<?> createEmergency(@RequestBody EmergencyRequestDTO dto) {
        // 400 Bad Request validation
        if (dto.getUserId() == null || dto.getPickupLocation() == null || dto.getContactNumber() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error_code", "INVALID_INPUT",
                            "message", "Mandatory fields (userId, contactNumber, pickupLocation) are missing.",
                            "timestamp", Instant.now().toString()
                    ));
        }

        // 409 Conflict check for active requests
        boolean hasActive = emergencyStore.values().stream()
                .anyMatch(e -> e.getUserId().equals(dto.getUserId()) && 
                          (e.getStatus().equals("SEARCHING_AMBULANCE") || e.getStatus().equals("ASSIGNED")));
        if (hasActive) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error_code", "ACTIVE_REQUEST_EXISTS",
                            "message", "An active emergency request already exists for this user.",
                            "timestamp", Instant.now().toString()
                    ));
        }

        String requestId = "emg_" + requestCounter.incrementAndGet();

        // Assign nearest available ambulance
        String assignedAmbId = ambulanceStore.values().stream()
                .filter(a -> a.getStatus().equals("AVAILABLE"))
                .map(Ambulance::getAmbulanceId)
                .findFirst()
                .orElse(null);

        String status = assignedAmbId != null ? "ASSIGNED" : "SEARCHING_AMBULANCE";

        if (assignedAmbId != null) {
            Ambulance amb = ambulanceStore.get(assignedAmbId);
            amb.setStatus("BUSY");
        }

        EmergencyRequest request = new EmergencyRequest(
                requestId,
                dto.getUserId(),
                dto.getPatientName(),
                dto.getContactNumber(),
                dto.getPickupLocation(),
                dto.getEmergencyType(),
                status,
                assignedAmbId,
                Instant.now().toString()
        );

        emergencyStore.put(requestId, request);

        // 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    /**
     * Task 1 & 4: Get Emergency Status API
     * GET /api/v1/emergencies/{request_id}
     */
    @GetMapping("/emergencies/{request_id}")
    public ResponseEntity<?> getEmergencyStatus(@PathVariable("request_id") String requestId) {
        EmergencyRequest request = emergencyStore.get(requestId);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error_code", "NOT_FOUND",
                            "message", "Emergency request ID not found.",
                            "timestamp", Instant.now().toString()
                    ));
        }
        // 200 OK
        return ResponseEntity.ok(request);
    }

    /**
     * Task 6: Additional API - Cancel Emergency Request
     * PATCH /api/v1/emergencies/{request_id}/cancel
     */
    @PatchMapping("/emergencies/{request_id}/cancel")
    public ResponseEntity<?> cancelEmergency(@PathVariable("request_id") String requestId, 
                                             @RequestBody(required = false) Map<String, String> body) {
        EmergencyRequest request = emergencyStore.get(requestId);
        if (request == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error_code", "NOT_FOUND",
                            "message", "Emergency request ID not found.",
                            "timestamp", Instant.now().toString()
                    ));
        }

        if (request.getStatus().equals("COMPLETED") || request.getStatus().equals("CANCELLED")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error_code", "INVALID_STATE",
                            "message", "Cannot cancel an already completed or cancelled request.",
                            "timestamp", Instant.now().toString()
                    ));
        }

        request.setStatus("CANCELLED");

        // Free up assigned ambulance if it was assigned
        if (request.getAssignedAmbulance() != null) {
            Ambulance amb = ambulanceStore.get(request.getAssignedAmbulance());
            if (amb != null) {
                amb.setStatus("AVAILABLE");
            }
        }

        // 200 OK
        return ResponseEntity.ok(Map.of(
                "request_id", requestId,
                "status", "CANCELLED",
                "cancelled_at", Instant.now().toString(),
                "message", "Emergency request has been cancelled successfully. Assigned driver notified."
        ));
    }

    /**
     * Task 1 & 4: Get Ambulance Details API
     * GET /api/v1/ambulances/{ambulance_id}
     */
    @GetMapping("/ambulances/{ambulance_id}")
    public ResponseEntity<?> getAmbulanceDetails(@PathVariable("ambulance_id") String ambulanceId) {
        Ambulance ambulance = ambulanceStore.get(ambulanceId);
        if (ambulance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error_code", "NOT_FOUND",
                            "message", "Ambulance ID not found.",
                            "timestamp", Instant.now().toString()
                    ));
        }
        // 200 OK
        return ResponseEntity.ok(ambulance);
    }

    /**
     * Task 1 & 4: Get Ambulance Location API
     * GET /api/v1/ambulances/{ambulance_id}/location
     */
    @GetMapping("/ambulances/{ambulance_id}/location")
    public ResponseEntity<?> getAmbulanceLocation(@PathVariable("ambulance_id") String ambulanceId) {
        Ambulance ambulance = ambulanceStore.get(ambulanceId);
        if (ambulance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error_code", "NOT_FOUND",
                            "message", "Ambulance ID not found.",
                            "timestamp", Instant.now().toString()
                    ));
        }
        // 200 OK
        return ResponseEntity.ok(Map.of(
                "ambulance_id", ambulanceId,
                "latitude", ambulance.getLatitude(),
                "longitude", ambulance.getLongitude(),
                "updated_at", Instant.now().toString()
        ));
    }
}

// ==========================================
// DATA TRANSFER OBJECTS & MODELS
// ==========================================
class EmergencyRequestDTO {
    private String userId;
    private String patientName;
    private String contactNumber;
    private Location pickupLocation;
    private String emergencyType;
    private String notes;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public Location getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(Location pickupLocation) { this.pickupLocation = pickupLocation; }
    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

class Location {
    private double latitude;
    private double longitude;
    private String addressText;

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getAddressText() { return addressText; }
    public void setAddressText(String addressText) { this.addressText = addressText; }
}

class EmergencyRequest {
    private String requestId;
    private String userId;
    private String patientName;
    private String contactNumber;
    private Location pickupLocation;
    private String emergencyType;
    private String status;
    private String assignedAmbulance;
    private String createdAt;

    public EmergencyRequest(String requestId, String userId, String patientName, String contactNumber,
                            Location pickupLocation, String emergencyType, String status,
                            String assignedAmbulance, String createdAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.patientName = patientName;
        this.contactNumber = contactNumber;
        this.pickupLocation = pickupLocation;
        this.emergencyType = emergencyType;
        this.status = status;
        this.assignedAmbulance = assignedAmbulance;
        this.createdAt = createdAt;
    }

    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public String getPatientName() { return patientName; }
    public String getContactNumber() { return contactNumber; }
    public Location getPickupLocation() { return pickupLocation; }
    public String getEmergencyType() { return emergencyType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    ---
    public String getAssignedAmbulance() { return assignedAmbulance; }
    public String getCreatedAt() { return createdAt; }
}

class Ambulance {
    private String ambulanceId;
    private String driverName;
    private String driverContact;
    private String vehicleType;
    private String status;
    private double latitude;
    private double longitude;

    public Ambulance(String ambulanceId, String driverName, String driverContact, String vehicleType,
                     String status, double latitude, double longitude) {
        this.ambulanceId = ambulanceId;
        this.driverName = driverName;
        this.driverContact = driverContact;
        this.vehicleType = vehicleType;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAmbulanceId() { return ambulanceId; }
    public String getDriverName() { return driverName; }
    public String getDriverContact() { return driverContact; }
    public String getVehicleType() { return vehicleType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
