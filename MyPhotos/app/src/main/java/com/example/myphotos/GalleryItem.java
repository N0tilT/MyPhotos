package com.example.myphotos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GalleryItem {

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private int imageId;
    private String imageBytes;
    private String imageDateAdded;
    private String imageFilename;
    private String imageExtension;
    private int userId;

    @JsonCreator
    public GalleryItem(@JsonProperty("image_bytes") String imageBytes,@JsonProperty("image_date_added") String imageDateAdded,@JsonProperty("image_filename") String imageFilename,@JsonProperty("image_fileextension") String imageExtension, @JsonProperty("user_id") int id){
        this.imageBytes = imageBytes;
        this.imageDateAdded = imageDateAdded;
        this.imageFilename = imageFilename;
        this.imageExtension = imageExtension;
        this.userId = id;
    }

    public int getImageId() {
        return imageId;
    }

    public String getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(String imageBytes) {
        this.imageBytes = imageBytes;
    }

    public String getImageDateAdded() {
        return imageDateAdded;
    }

    public void setImageDateAdded(String imageDateAdded) {
        this.imageDateAdded = imageDateAdded;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public void setImageFilename(String imageFilename) {
        this.imageFilename = imageFilename;
    }

    public String getImageExtension() {
        return imageExtension;
    }

    public void setImageExtension(String imageExtension) {
        this.imageExtension = imageExtension;
    }

    public int getUserId() {
        return userId;
    }
}
