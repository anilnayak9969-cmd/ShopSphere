package com.example.ecommerce.dtos.common;

/** url is relative (/api/images/...) so it keeps working if the backend's host name changes. */
public record UploadResponse(String url, String contentType, long sizeBytes) {
}
