package ml.melun.mangaview.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ml.melun.mangaview.adapter.StripAdapter;
import ml.melun.mangaview.model.PageItem;

public class StripLayoutManager extends NpaLinearLayoutManager {
    StripAdapter adapter;

    public StripLayoutManager(Context context) {
        super(context);
    }

    public StripLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    public StripLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        adapter = (StripAdapter) view.getAdapter();
    }

    @Override
    public void onAdapterChanged(@Nullable RecyclerView.Adapter oldAdapter, @Nullable RecyclerView.Adapter newAdapter) {
        super.onAdapterChanged(oldAdapter, newAdapter);
        adapter = (StripAdapter) newAdapter;
    }

    public void scrollToPage(PageItem page) {
        List<Object> items = adapter.getItems();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof PageItem) {
                if (((PageItem) item).equals(page))
                    scrollToPosition(i);
            }
        }
    }

}
