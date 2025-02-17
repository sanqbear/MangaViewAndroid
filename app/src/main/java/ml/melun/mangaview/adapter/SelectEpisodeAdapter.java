package ml.melun.mangaview.adapter;

import android.content.Context;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.Arrays;
import java.util.List;

import ml.melun.mangaview.R;
import ml.melun.mangaview.mangaview.Manga;

import static ml.melun.mangaview.MainApplication.p;

public class SelectEpisodeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Manga> data;
    private final LayoutInflater mInflater;
    private final Context mainContext;
    boolean favorite = false;
    TypedValue outValue;
    boolean[] selected;
    ItemClickListener mClickListener;
    boolean dark;
    boolean single = true;
    int rs = -1, re = -1;

    // data is passed into the constructor
    public SelectEpisodeAdapter(Context context, List<Manga> list) {
        this.mInflater = LayoutInflater.from(context);
        mainContext = context;
        this.data = list;
        outValue = new TypedValue();
        selected = new boolean[list.size()];
        Arrays.fill(selected, Boolean.FALSE);
        dark = p.getDarkTheme();
        mainContext.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    // inflates the row layout from xml when needed
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_episode, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ViewHolder h = (ViewHolder) holder;
        try {
            Manga m = data.get(position);
            h.episode.setText(m.getName());
            h.date.setText(m.getDate());
            if (selected[position]) {
                if (dark)
                    h.itemView.setBackgroundColor(ContextCompat.getColor(mainContext, R.color.selectedDark));
                else
                    h.itemView.setBackgroundColor(ContextCompat.getColor(mainContext, R.color.selected));
            } else {
                h.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            if (position == rs || position == re) {
                h.itemView.setBackgroundColor(ContextCompat.getColor(mainContext, R.color.rangeSelected));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return data.size();
    }

    public void select(int position) {
        if (single) {
            selected[position] = !selected[position];
            notifyItemChanged(position);
        } else {
            // range start
            if (rs == -1 && re == -1) {
                rs = position;
            }
            // selected pos = range start
            else if (position == rs) {
                rs = -1;
                re = -1;
            }
            // range end
            else if (rs != -1 && re == -1) {
                re = position;
                while (rs != re) {
                    selected[rs] = !selected[rs];
                    notifyItemChanged(rs);

                    if (rs > re)
                        rs--;
                    else
                        rs++;
                }
                selected[rs] = !selected[rs];
                notifyItemChanged(rs);

                rs = -1;
                re = -1;
            }
            notifyItemChanged(rs);
            notifyItemChanged(re);
            notifyItemChanged(position);
        }
    }

    public void setSelectionMode(boolean single) {
        this.single = single;
        int tmps = rs;
        int tmpe = re;
        rs = -1;
        re = -1;
        notifyItemChanged(tmps);
        notifyItemChanged(tmpe);
    }

    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView episode, date;

        ViewHolder(View itemView) {
            super(itemView);
            episode = itemView.findViewById(R.id.episode);
            date = itemView.findViewById(R.id.date);
            if (dark) {
                date.setTextColor(Color.WHITE);
                episode.setTextColor(Color.WHITE);
            } else {
                date.setTextColor(Color.BLACK);
                episode.setTextColor(Color.BLACK);
            }
            itemView.setOnClickListener(v -> mClickListener.onItemClick(v, getAdapterPosition()));
        }
    }

    public void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public JSONArray getSelected(boolean all) {
        JSONArray tmp = new JSONArray();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i])
                tmp.put(i);
            else if (all)
                tmp.put(i);
        }
        return tmp;
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }
}
