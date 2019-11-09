package com.george.soloupis_pneumothorax.customview;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.george.soloupis_pneumothorax.R;
import com.squareup.picasso.Picasso;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.NavigationAdapterViewHolder> {

    private Context mContext;
    private WheelsClickItemListener wheelsClickItemListener;

    public RecyclerViewAdapter(Context context, WheelsClickItemListener listener) {
        mContext = context;
        wheelsClickItemListener = listener;
    }

    public interface WheelsClickItemListener {
        void onListItemClick(int itemIndex);
    }

    @NonNull
    @Override
    public NavigationAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        return new NavigationAdapterViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.wheels_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NavigationAdapterViewHolder holder, int position) {
        if (position == 0) {
            Picasso.get().load("file:///android_asset/m5.png").into(holder.iconView);
        } else if(position == 1){
            Picasso.get().load("file:///android_asset/m6.png").into(holder.iconView);
        }else if(position == 2){
            Picasso.get().load("file:///android_asset/m8.png").into(holder.iconView);
        }

        //Setting tag
        holder.itemView.setTag(position);
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    class NavigationAdapterViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        final ImageView iconView;

        NavigationAdapterViewHolder(View itemView) {
            super(itemView);

            iconView = itemView.findViewById(R.id.imageViewWheelAdapter);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            wheelsClickItemListener.onListItemClick((int) itemView.getTag());
        }
    }
}
