package org.sda.restaurantservice.dto;

/**
 * What the browser needs to upload a photo straight to Cloudinary with a signed request.
 * The API secret never leaves this service - the client only ever sees the derived signature.
 */
public record UploadSignatureResponse(
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String folder,
        String uploadUrl) {
}
