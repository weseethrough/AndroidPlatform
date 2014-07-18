package com.raceyourself.raceyourself.home.feed;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.collect.ImmutableList;
import com.raceyourself.raceyourself.R;

import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Duncan on 18/07/2014.
 */
@Slf4j
public class ActivityAdapter extends ChallengeListAdapter {

    private static final long HEADER_ID = 89549822033333333L;

    @Getter
    private ArrayAdapter<ChallengeNotificationBean> delegate;

    public static ActivityAdapter create(@NonNull Context context, @NonNull List<ChallengeNotificationBean> items) {
        String titleText = context.getString(R.string.home_feed_title_activity);
        return new ActivityAdapter(context, items, titleText);
    }

    private ActivityAdapter(
            @NonNull Context context, @NonNull List<ChallengeNotificationBean> items, @NonNull String title) {
        super(context, items, title, HEADER_ID);
    }


    @Override
    public boolean areAllItemsEnabled() {
        return delegate.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return delegate.isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        delegate.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        delegate.unregisterDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return delegate.getCount();
    }

    @Override
    public ChallengeNotificationBean getItem(int position) {
        return delegate.getItem(position);
    }

    @Override
    protected void insert(ChallengeNotificationBean a, int index) {
        delegate.insert(a, index);
    }

    @Override
    protected void remove(ChallengeNotificationBean a) {
        delegate.remove(a);
    }

    @Override
    protected void addAll(List<ChallengeNotificationBean> challengeNotificationBeans) {
        delegate.addAll(challengeNotificationBeans);
    }

    @Override
    protected void clear() {
        delegate.clear();
    }

    @Override
    public long getItemId(int position) {
        return delegate.getItemId(position);
    }

    @Override
    public boolean hasStableIds() {
        return delegate.hasStableIds();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return delegate.getView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return delegate.getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return delegate.getViewTypeCount();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
}
