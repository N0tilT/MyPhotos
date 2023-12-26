package com.example.myphotos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ViewHolder> {

    private final Context mContext;
    private final ArrayList<GalleryItem> mGalleryItems;

    public GalleryAdapter(Context context, ArrayList<GalleryItem> galleryItems) {
        this.mContext = context;
        this.mGalleryItems = galleryItems;
    }

    @NonNull
    @Override
    public GalleryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.gallery_item_layout,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryAdapter.ViewHolder holder, int position) {
        GalleryItem model = mGalleryItems.get(position);
        String[] byteValues = model.getImageBytes().split(",");
        byte[] bytes = new byte[model.getImageBytes().length()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = Byte.parseByte(byteValues[i].trim());
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        holder.galleryImage.setImageBitmap(bmp);
    }

    @Override
    public int getItemCount() {
        return mGalleryItems.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView galleryImage;
        private final TextView imageFilename;
        private final TextView imageDateAdded;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            galleryImage = itemView.findViewById(R.id.gallery_image);
            imageFilename = itemView.findViewById(R.id.image_filename);
            imageDateAdded = itemView.findViewById(R.id.image_date_added);
        }
    }
}
