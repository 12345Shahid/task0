package com.rcl.androidstreamcontrol;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.messageText.setText(message.getText());
        holder.messageTime.setText(message.getTime());

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(32f);

        if (message.isUser()) {
            holder.messageContainer.setGravity(Gravity.END);
            shape.setColor(Color.parseColor("#1A1A2E"));
            shape.setStroke(2, Color.parseColor("#D81B60"));
        } else {
            holder.messageContainer.setGravity(Gravity.START);
            shape.setColor(Color.parseColor("#0A2222"));
            shape.setStroke(2, Color.parseColor("#00E5FF"));
        }

        holder.bubbleLayout.setBackground(shape);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer;
        LinearLayout bubbleLayout;
        TextView messageText;
        TextView messageTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.message_container);
            bubbleLayout = itemView.findViewById(R.id.bubble_layout);
            messageText = itemView.findViewById(R.id.message_text);
            messageTime = itemView.findViewById(R.id.message_time);
        }
    }
}
