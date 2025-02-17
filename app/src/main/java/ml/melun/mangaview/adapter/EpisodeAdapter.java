package ml.melun.mangaview.adapter;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import android.widget.ImageView;

import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import ml.melun.mangaview.ui.NpaLinearLayoutManager;
import ml.melun.mangaview.R;
import ml.melun.mangaview.mangaview.Manga;
import ml.melun.mangaview.mangaview.Title;

import static ml.melun.mangaview.MainApplication.p;

public class EpisodeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Manga> mData;
    private final LayoutInflater mInflater;
    private ItemClickListener mClickListener;
    private final Context mainContext;
    boolean favorite = false;
    boolean bookmarked = false;
    TypedValue outValue;
    private int bookmark = -1;
    // title is in index 0
    Title title;
    TagAdapter ta;
    NpaLinearLayoutManager lm;
    boolean dark;
    boolean save;
    int mode = 0;
    boolean login;

    boolean bookmarkSubmitting = false;

    // data is passed into the constructor
    public EpisodeAdapter(Context context, List<Manga> data, Title title, int mode) {
        this.mInflater = LayoutInflater.from(context);
        mainContext = context;
        this.mData = data;
        this.title = title;
        this.mode = mode;
        outValue = new TypedValue();
        dark = p.getDarkTheme();
        save = p.getDataSave();
        bookmarked = title.getBookmarked();
        mainContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        if (title.getTags() != null) {
            ta = new TagAdapter(context, title.getTags());
            lm = new NpaLinearLayoutManager(context);
            lm.setOrientation(LinearLayoutManager.HORIZONTAL);
        }
        setHasStableIds(true);
        if (mode != 0)
            save = false;
        login = mode == 0 && p.getLogin() != null && p.getLogin().isValid();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0)
            return 0;
        else
            return 1;
    }

    // inflates the row layout from xml when needed
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        if (viewType == 0) {
            view = mInflater.inflate(R.layout.item_header, parent, false);
            return new HeaderHolder(view);
        } else {
            view = mInflater.inflate(R.layout.item_episode, parent, false);
            return new ViewHolder(view);
        }
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (position == 0) {
            HeaderHolder h = (HeaderHolder) holder;
            String titles = this.title.getName();
            String thumb = this.title.getThumb();
            String release = this.title.getRelease();
            h.h_title.setText(titles);
            h.h_author.setText(this.title.getAuthor());
            if (release != null || release.length() > 0)
                h.h_release.setText(release);
            else
                h.h_release.setText("");
            if (favorite)
                h.h_star_icon.setImageResource(R.drawable.ic_favorite);
            else
                h.h_star_icon.setImageResource(R.drawable.ic_favorite_border);
            if (bookmarked)
                h.h_bookmark_icon.setImageResource(R.drawable.ic_bookmark);
            else
                h.h_bookmark_icon.setImageResource(R.drawable.ic_bookmark_border);

            if (!save)
                Glide.with(h.h_thumb)
                        .load(thumb)
                        .apply(new RequestOptions().dontTransform())
                        .into(h.h_thumb);
            if (mode == 0 || mode == 3)
                h.h_star.setVisibility(View.VISIBLE);
            else
                h.h_star.setVisibility(View.GONE);

            if (mode == 0) {
                // set ext-info text
                h.h_recommend_c.setText(String.valueOf(title.getRecommend_c()));

            } else {
                // offline manga
                h.h_bookmark.setVisibility(View.GONE);
                h.h_recommend.setVisibility(View.GONE);
                h.h_recommend_c.setVisibility(View.GONE);
            }

        } else {
            ViewHolder h = (ViewHolder) holder;
            int Dposition = position - 1;
            h.episode.setText(mData.get(Dposition).getName());
            h.date.setText(mData.get(Dposition).getDate());
            if (position == bookmark) {
                if (dark)
                    h.itemView.setBackgroundColor(ContextCompat.getColor(mainContext, R.color.selectedDark));
                else
                    h.itemView.setBackgroundColor(ContextCompat.getColor(mainContext, R.color.selected));
            } else {
                h.itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    public void toggleBookmark(boolean success) {
        if (success) {
            this.bookmarked = !this.bookmarked;
        }
        this.bookmarkSubmitting = false;
        notifyItemChanged(0);
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size() + 1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView episode, date;

        ViewHolder(View itemView) {
            super(itemView);
            episode = itemView.findViewById(R.id.episode);
            date = itemView.findViewById(R.id.date);
            itemView.setOnClickListener(v -> {

                Manga m = mData.get(getAdapterPosition() - 1);
                if (m.getId() > -1) {
                    if (bookmark != -1) {
                        int pre = bookmark;
                        notifyItemChanged(pre);
                    }
                    bookmark = getAdapterPosition();
                    notifyItemChanged(bookmark);
                }
                mClickListener.onItemClick(getAdapterPosition() - 1, m);
            });
        }
    }

    public class HeaderHolder extends RecyclerView.ViewHolder {
        TextView h_title, h_author, h_release;
        ImageView h_thumb;
        ImageView h_star_icon;
        ImageView h_bookmark_icon;

        Button h_first;
        RecyclerView h_tags;
        View h_bookmark, h_star, h_recommend;

        TextView h_recommend_c;

        HeaderHolder(View itemView) {
            super(itemView);
            h_title = itemView.findViewById(R.id.HeaderTitle);
            h_thumb = itemView.findViewById(R.id.HeaderThumb);
            h_star_icon = itemView.findViewById(R.id.favoriteIcon);
            h_first = itemView.findViewById(R.id.HeaderFirst);
            h_tags = itemView.findViewById(R.id.tagsContainer);
            h_author = itemView.findViewById(R.id.headerAuthor);
            h_release = itemView.findViewById(R.id.HeaderRelease);
            h_bookmark_icon = itemView.findViewById(R.id.bookmarkIcon);

            h_star = itemView.findViewById(R.id.HeaderFavorite);
            h_bookmark = itemView.findViewById(R.id.HeaderBookmark);
            h_recommend = itemView.findViewById(R.id.recommendIcon);

            h_recommend_c = itemView.findViewById(R.id.recommendText);

            h_bookmark.setOnClickListener(v -> {
                // set bookmark
                if (login) {
                    if (!bookmarkSubmitting) {
                        mClickListener.onBookmarkClick();
                        bookmarkSubmitting = true;
                    }
                } else {
                    mClickListener.onBookmarkClick();
                    bookmarkSubmitting = false;
                }
            });
            h_star.setOnClickListener(v -> mClickListener.onStarClick());
            h_first.setOnClickListener(v -> mClickListener.onFirstClick());
            h_author.setOnClickListener(v -> mClickListener.onAuthorClick());
            if (ta != null) {
                h_tags.setLayoutManager(lm);
                h_tags.setAdapter(ta);
            }
        }
    }

    public void setFavorite(boolean b) {
        if (favorite != b) {
            favorite = b;
            notifyItemChanged(0);
        }
    }

    public void setBookmark(int i) {
        // THIS SHOULD BE SET TO INDEX, NOT ID! : because of notifyitemChanged
        // i is real index in recyclerview
        if (i != bookmark) {
            int tmp = bookmark;
            bookmark = i;
            if (tmp > 0)
                notifyItemChanged(tmp);
            if (bookmark > 0)
                notifyItemChanged(bookmark);
        }
    }

    // allows clicks events to be caught
    public void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public void setTagClickListener(TagAdapter.tagOnclick t) {
        ta.setClickListener(t);
    }

    // parent activity will implement this method to respond to click events
    public interface ItemClickListener {
        void onItemClick(int position, Manga m);

        void onStarClick();

        void onFirstClick();

        void onAuthorClick();

        void onBookmarkClick();
    }
}
