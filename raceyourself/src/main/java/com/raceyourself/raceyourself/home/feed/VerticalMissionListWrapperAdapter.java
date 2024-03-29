package com.raceyourself.raceyourself.home.feed;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.raceyourself.platform.models.Mission;
import com.raceyourself.raceyourself.R;

import java.util.ArrayList;
import java.util.List;

import it.sephiroth.android.library.widget.HListView;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

/**
 * The horizontally-scrolling list fills exactly one row of the master, vertical list, irrespective of how many elements
 * it contains. This one row contains a HListView, managed by a HorizontalMissionListAdapter.
 *
 * Created by Duncan on 10/07/2014.
 */
@Slf4j
public class VerticalMissionListWrapperAdapter extends ArrayFeedListAdapter<VerticalMissionListWrapperAdapter.DummyVerticalMissionBean>
        implements HorizontalMissionListAdapter.OnFragmentInteractionListener {

    private static final long HEADER_ID = 88043278183335L;

    private final Context context;

    private HorizontalMissionListAdapter adapter = null;

    private int stars = 0;

    @Setter
    HorizontalMissionListAdapter.OnFragmentInteractionListener onFragmentInteractionListener;

    public static VerticalMissionListWrapperAdapter create(@NonNull Context context, int textViewResourceId) {
        String titleText = context.getString(R.string.home_feed_title_missions);
        return new VerticalMissionListWrapperAdapter(
                context, textViewResourceId, titleText, Lists.newArrayList(new DummyVerticalMissionBean()));
    }

    private VerticalMissionListWrapperAdapter(@NonNull Context context,
                                              int resource,
                                              @NonNull String titleText,
                                              @NonNull List<DummyVerticalMissionBean> items) {
        super(context, titleText, HEADER_ID, resource, items);
        this.context = context;

        setStickyHeaderBackgroundColor(0xfff1f0eb);

        calculateStars();
    }

    public void refresh() {
        if (adapter == null) return;
        List<Mission> missions = Mission.getMissions();
        List<MissionBean> missionBeans = MissionBean.from(missions);
        adapter.mergeItems(missionBeans);
        calculateStars();
        getDelegate().notifyDataSetInvalidated();
    }

    private int calculateStars() {
        stars = 0;
        for (Mission mission : Mission.getMissions()) {
            Mission.MissionLevel level = mission.getCurrentLevel();
            if (level == null) continue;
            // One star per completed level
            int mStars = level.level - 1;
            if (level.isClaimed()) mStars++;
            stars += mStars;
        }
        return stars;
    }

    LinearLayout vWrapper;
    @Override
    public View getView(int groupPosition, View convertView, ViewGroup parent) {
        View cachedView = null;
        if (convertView instanceof LinearLayout) cachedView = convertView;

        if(cachedView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            vWrapper = (LinearLayout) inflater.inflate(R.layout.fragment_mission_list, null);

            HListView hListView = (HListView) vWrapper.findViewById(R.id.missionList);

            List<Mission> missions = Mission.getMissions();
            List<MissionBean> missionBeans = MissionBean.from(missions);

            adapter = new HorizontalMissionListAdapter(
                    context, R.layout.fragment_mission_list, missionBeans);
            adapter.setOnFragmentInteractionListener(this);

            hListView.setAdapter(adapter);

        }
        else {
            vWrapper = (LinearLayout) cachedView;
        }

        // Mission list appears exactly once in horizontal list. Thus cannot be recycled by Android, thus
        // if non-null, we know it's already set up correctly.

        return vWrapper;
    }

    public void setMissionSelection(int selection) {
        if (vWrapper == null) getView(1,null,null);
        final HListView hListView = (HListView) vWrapper.findViewById(R.id.missionList);
        log.warn("Smooth scrolling from element " + (hListView.getCount()-1) + " to element 0");
        hListView.setSelection(4);
        //hListView.smoothScrollToPosition(0,0);

        // smooth scroll back to (almost) beginning after short wait
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hListView.smoothScrollBy(-875, 700, true);
            }
        }, 500);

    }

    public int getMissionCount() {
        if (vWrapper == null) getView(1,null,null);
        HListView hListView = (HListView) vWrapper.findViewById(R.id.missionList);
        return hListView.getCount();
    }

    @Override
    public View getHeaderView(int i, View convertView, ViewGroup parent) {
        convertView = super.getHeaderView(i, convertView, parent);

        View missions = convertView.findViewById(R.id.missionsProgress);
        TextView missionTotalStars = (TextView)convertView.findViewById(R.id.missionTotalStars);
        missionTotalStars.setText(String.valueOf(stars));
        missions.setVisibility(View.VISIBLE);

        return convertView;
    }

    @Override
    public void onFragmentInteraction(MissionBean mission, View view) {
        if (onFragmentInteractionListener != null) onFragmentInteractionListener.onFragmentInteraction(mission, view);
    }

    public static class DummyVerticalMissionBean implements HomeFeedRowBean {
    }
}
