package uz.pdp.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for managing image storage operations using Amazon S3.
 * Handles uploading, retrieving, and deleting images for doors and other
 * entities.
 * Implements secure file handling and proper error management.
 *
 * @version 1.0
 * @since 2025-01-17
 */
@Service
public class ImageStorageService {
    private static final Logger logger = LoggerFactory.getLogger(ImageStorageService.class);
    private static final String DOOR_IMAGES_PREFIX = "doors/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_CONTENT_TYPES = {
            "image/jpeg", "image/png", "image/gif"

    };

    private final AmazonS3 s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public ImageStorageService(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Uploads a door image to Amazon S3.
     * Generates a unique filename and validates file type and size.
     *
     * @param file Image file to upload
     * @return URL of the uploaded image
     * @throws IllegalArgumentException if file is invalid
     * @throws IOException              if file processing fails
     */
    public String storeImage(MultipartFile file) throws IOException {
        validateFile(file);

        String filename = generateUniqueFilename(file);
        String key = DOOR_IMAGES_PREFIX + filename;

        try {
            logger.info("Uploading door image: {}", filename);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            s3Client.putObject(bucketName, key, file.getInputStream(), metadata);
            String imageUrl = s3Client.getUrl(bucketName, key).toString();

            logger.info("Successfully uploaded door image: {}", filename);
            return imageUrl;
        } catch (AmazonServiceException e) {
            logger.error("Failed to upload door image: {}", e.getMessage());
            throw new IOException("Failed to upload image to S3", e);
        }
    }

    /**
     * Deletes an image from Amazon S3.
     *
     * @param imageUrl URL of the image to delete
     * @throws IllegalArgumentException if URL is invalid
     */
    public void deleteImage(String imageUrl) {
        try {
            String key = extractKeyFromUrl(imageUrl);
            logger.info("Deleting image with key: {}", key);

            s3Client.deleteObject(bucketName, key);
            logger.info("Successfully deleted image: {}", key);
        } catch (AmazonServiceException e) {
            logger.error("Failed to delete image: {}", e.getMessage());
            throw new IllegalStateException("Failed to delete image from S3", e);
        }
    }

    /**
     * Validates file properties including size and content type.
     * Because we can't let just any file sneak into our S3 bucket! 🕵️‍♂️
     * 
     * Think of this as our bouncer - checking IDs and making sure no troublemakers
     * get in.
     * Size matters! We keep things under 5MB because we're not made of money 💰
     * 
     * @param file File to validate - better be an image or it's getting bounced!
     * @throws IllegalArgumentException if validation fails (sorry, no fake IDs
     *                                  allowed)
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            logger.error("Empty file provided");
            throw new IllegalArgumentException("Hey, you can't upload nothing! We need actual pixels here! 🖼️");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            logger.error("File size exceeds limit: {} bytes", file.getSize());
            throw new IllegalArgumentException(
                    "Whoa there! That file is too thick (over 5MB)! Put it on a diet first! 🏋️‍♂️");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.equals("application/octet-stream")) {
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
                switch (extension) {
                    case "jpg":
                    case "jpeg":
                        contentType = "image/jpeg";
                        break;
                    case "png":
                        contentType = "image/png";
                        break;
                    case "gif":
                        contentType = "image/gif";
                        break;
                    default:
                        logger.error("Invalid file extension: {}", extension);
                        throw new IllegalArgumentException(
                                "Sorry, this file type isn't on the guest list! Only JPG, PNG, and GIF are VIP! 🎭");
                }
            }
        }

        boolean isValidContentType = false;
        for (String allowedType : ALLOWED_CONTENT_TYPES) {
            if (allowedType.equals(contentType)) {
                isValidContentType = true;
                break;
            }
        }

        if (!isValidContentType) {
            logger.error("Invalid content type: {}", contentType);
            throw new IllegalArgumentException(
                    "Nice try, but we only accept real images here! JPG, PNG, or GIF - pick your fighter! 🥊");
        }
    }

    /**
     * Generates a unique filename for the uploaded file.
     *
     * @param file Original file
     * @return Generated unique filename
     */
    private String generateUniqueFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }

    /**
     * Extracts the S3 key from a full URL.
     *
     * @param url Full S3 URL
     * @return Extracted key
     * @throws IllegalArgumentException if URL is invalid
     */
    private String extractKeyFromUrl(String url) {
        try {
            String[] parts = url.split(bucketName + "/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid S3 URL format");
            }
            return parts[1];
        } catch (Exception e) {
            logger.error("Failed to extract key from URL: {}", url);
            throw new IllegalArgumentException("Invalid S3 URL", e);
        }
    }
}