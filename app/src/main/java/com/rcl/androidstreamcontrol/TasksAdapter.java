package com.rcl.androidstreamcontrol;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.ViewHolder> {

    private final List<TaskItem> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onToggleCompleted(TaskItem item, boolean isCompleted);
        void onDeleteClick(TaskItem item);
    }

    public TasksAdapter(List<TaskItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TaskItem item = items.get(position);
        holder.title.setText(item.title);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.isCompleted);
        
        if (item.isCompleted) {
            holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.title.setTextColor(0xFF888899);
        } else {
            holder.title.setPaintFlags(holder.title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.title.setTextColor(0xFFFFFFFF);
        }

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onToggleCompleted(item, isChecked);
        });

        holder.deleteIcon.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView title;
        ImageView deleteIcon;

        public ViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.item_checkbox);
            title = itemView.findViewById(R.id.item_title);
            deleteIcon = itemView.findViewById(R.id.item_delete);
        }
    }
}
