package ml.melun.mangaview.adapter;

import android.content.Context;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

import ml.melun.mangaview.R;
import ml.melun.mangaview.mangaview.Comment;

import static ml.melun.mangaview.MainApplication.p;

public class CommentsAdapter extends BaseAdapter {
    Context context;
    ArrayList<Comment> data;
    LayoutInflater inflater;
    boolean dark;
    boolean save;

    public CommentsAdapter(Context context, ArrayList<Comment> data) {
        super();
        this.dark = p.getDarkTheme();
        this.save = p.getDataSave();
        this.context = context;
        this.data = data;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_comment, parent, false);
        }
        Comment c = data.get(position);
        ConstraintLayout layout = convertView.findViewById(R.id.comment_layout);
        ImageView icon = convertView.findViewById(R.id.comment_icon);
        TextView content = convertView.findViewById(R.id.comment_content);
        TextView timeStamp = convertView.findViewById(R.id.comment_time);
        TextView user = convertView.findViewById(R.id.comment_user);
        TextView likes = convertView.findViewById(R.id.comment_likes);
        TextView level = convertView.findViewById(R.id.comment_level);

        layout.setPadding(60 * c.getIndent(), 0, 0, 0);
        if (c.getIcon().length() > 1 && !save)
            Glide.with(icon).load(c.getIcon()).into(icon);
        else
            icon.setImageResource(R.drawable.user);
        content.setText(c.getContent());
        timeStamp.setText(c.getTimestamp());
        user.setText(c.getUser());
        level.setText(String.valueOf(c.getLevel()));
        if (c.getLikes() > 0)
            likes.setText(String.valueOf(c.getLikes()));
        else
            likes.setText("");
        return convertView;
    }

    @Override
    public Comment getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }
}
