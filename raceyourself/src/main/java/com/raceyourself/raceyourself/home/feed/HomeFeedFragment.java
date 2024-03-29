package com.raceyourself.raceyourself.home.feed;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.nhaarman.listviewanimations.itemmanipulation.ExpandCollapseListener;
import com.raceyourself.platform.gpstracker.SyncHelper;
import com.raceyourself.platform.models.AccessToken;
import com.raceyourself.platform.models.Challenge;
import com.raceyourself.platform.models.Event;
import com.raceyourself.platform.models.Notification;
import com.raceyourself.platform.models.Track;
import com.raceyourself.platform.models.User;
import com.raceyourself.raceyourself.MobileApplication;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.util.PictureUtils;
import com.raceyourself.raceyourself.base.util.StringFormattingUtils;
import com.raceyourself.raceyourself.game.GameActivity;
import com.raceyourself.raceyourself.game.GameConfiguration;
import com.raceyourself.raceyourself.home.ChallengeVersusAnimator;
import com.raceyourself.raceyourself.home.ExpandCollapseListenerGroup;
import com.raceyourself.raceyourself.home.UserBean;
import com.squareup.picasso.Picasso;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

@Slf4j
@EFragment(R.layout.fragment_challenge_list)
public class HomeFeedFragment extends Fragment implements AdapterView.OnItemClickListener, HorizontalMissionListAdapter.OnFragmentInteractionListener {
    /**
     * How long do we show expired challenges for before clearing them out? Currently disabled (i.e. no expired
     * challenges at all).
     */
    public static final int DAYS_RETENTION = 0;
    private static final int MAX_ACTIVITY_ITEMS = 15;

    private ChallengeListRefreshHandler challengeListRefreshHandler = new ChallengeListRefreshHandler();
    public static final String MESSAGING_MESSAGE_REFRESH = "refresh";

    private OnFragmentInteractionListener listener;
    private Activity activity;
    @Getter
    private ExpandableChallengeListAdapter inboxListAdapter;
    private InboxEmptyAdapter inboxEmptyAdapter;
    @Getter
    private ExpandableChallengeListAdapter runListAdapter;
    private RunnableEmptyAdapter runnableEmptyAdapter;
    private ActivityAdapter activityAdapter;
    @Getter
    private VerticalMissionListWrapperAdapter verticalMissionListWrapperAdapter;
    @Getter
    private HomeFeedCompositeListAdapter compositeListAdapter;
    private AutomatchAdapter automatchAdapter;
    private RaceYourselfAdapter raceYourselfAdapter;
    @Getter
    private List<ChallengeNotificationBean> notifications;

    @InstanceState
    ChallengeDetailBean selectedChallenge;
    private List<ChallengeVersusAnimator> versusAnimators = Lists.newLinkedList();

    @ViewById(R.id.challengeList)
    StickyListHeadersListView stickyListView;

    @ViewById(R.id.playerProfilePic)
    ImageView playerPic;

    @ViewById(R.id.playerRank)
    ImageView rankIcon;

    @ViewById(R.id.playerName)
    TextView playerName;

    @ViewById
    TextView vsTextview;

    @ViewById(R.id.runButton)
    ImageButton raceNowButton;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public HomeFeedFragment() {
    }

    private Predicate<ChallengeNotificationBean> activeOrRecentPredicate = new Predicate<ChallengeNotificationBean>() {
        @Override
        public boolean apply(ChallengeNotificationBean input) {
            // Filter out challenges that expired more than DAYS_RETENTION ago.
            return !input.getExpiry().plusDays(DAYS_RETENTION).isBeforeNow();
        }
    };

    private List<ChallengeNotificationBean> inboxFilter(List<ChallengeNotificationBean> unfiltered) {
        return Lists.newArrayList(Iterables.filter(
                Iterables.filter(unfiltered, new Predicate<ChallengeNotificationBean>() {
            @Override
            public boolean apply(ChallengeNotificationBean input) {
                return input.isInbox();
            }
        }), activeOrRecentPredicate));
    }

    private List<ChallengeNotificationBean> runFilter(List<ChallengeNotificationBean> unfiltered) {
        return Lists.newArrayList(Iterables.filter(
                Iterables.filter(unfiltered, new Predicate<ChallengeNotificationBean>() {
                    @Override
                    public boolean apply(ChallengeNotificationBean input) {
                        return input.isRunnableNow();
                    }
                }), activeOrRecentPredicate));
    }

    private List<ChallengeNotificationBean> activityFilter(List<ChallengeNotificationBean> unfiltered) {
        List<ChallengeNotificationBean> activity =
                Lists.newArrayList(Iterables.filter(unfiltered, new Predicate<ChallengeNotificationBean>() {
                    @Override
                    public boolean apply(ChallengeNotificationBean input) {
                        return input.isActivity();
                    }
                }));

        // we want the user to feel like the app is being actively used - so we don't filter out old challenges here
        // after a couple of days like with the user's own challenges. Instead, we limit the list to at most N elements.
        if (activity.size() > MAX_ACTIVITY_ITEMS)
            activity = activity.subList(0, MAX_ACTIVITY_ITEMS);
        return activity;
    }

    private ListView listView;

    @AfterViews
    void afterInject() {
        int offset = 0; // sub-list offset

        // So much faff to include/exclude these headers - let's just have it disabled rather than ripping it out
        // entirely - easy to reintroduce later.
        // TODO actual desired functionality is to just unstick 'missions'. Could maybe achieve this with a callback
        // that enables disables stickyness depending on current position in list...
        stickyListView.setAreHeadersSticky(false);

        listView = stickyListView.getWrappedList();

        notifications = ImmutableList.copyOf(sortChallengeNotifications(ChallengeNotificationBean.from(
                                Notification.getNotificationsByType("challenge"))));

        // ///////////////// INBOX /////////////////

        // unread and received challenges
        List<ChallengeNotificationBean> filteredNotifications = inboxFilter(notifications);
        inboxListAdapter = new ExpandableChallengeListAdapter(getActivity(), filteredNotifications,
                activity.getString(R.string.home_feed_title_inbox), 4732989818333L);

        inboxListAdapter.setAbsListView(listView, offset);
        offset += filteredNotifications.size();

        inboxEmptyAdapter = InboxEmptyAdapter.create(getActivity(), R.layout.fragment_challenge_list,
                activity.getString(R.string.home_feed_title_inbox), 4732989818333L);
        if (inboxListAdapter.isEmpty()) inboxEmptyAdapter.show();
        else inboxEmptyAdapter.hide();
        offset += inboxEmptyAdapter.getCount();

        ///////////////// MISSIONS /////////////////

        verticalMissionListWrapperAdapter =
                VerticalMissionListWrapperAdapter.create(getActivity(), android.R.layout.simple_list_item_1);
        verticalMissionListWrapperAdapter.setOnFragmentInteractionListener(this);
        offset++;

        ///////////////// RUN! /////////////////

        // Race yourself.
        raceYourselfAdapter = RaceYourselfAdapter.create(getActivity(), R.layout.fragment_challenge_list);
        offset++;

        // Read or sent challenges
        filteredNotifications = runFilter(notifications);
        runListAdapter = new ExpandableChallengeListAdapter(
                getActivity(), filteredNotifications, activity.getString(R.string.home_feed_title_run),
                AutomatchAdapter.HEADER_ID);

        runListAdapter.setAbsListView(listView, offset);
        offset += filteredNotifications.size();

        runnableEmptyAdapter = RunnableEmptyAdapter.create(getActivity(), R.layout.fragment_challenge_list,
                activity.getString(R.string.home_feed_title_run), AutomatchAdapter.HEADER_ID);
        if (runListAdapter.isEmpty()) runnableEmptyAdapter.show();
        else runnableEmptyAdapter.hide();
        offset += runnableEmptyAdapter.getCount();

        // Automatch. Similar presentation to 'run', but can't be in the same adapter as it mustn't be made
        // subject to ChallengeListAdapter.mergeItems().
        automatchAdapter = AutomatchAdapter.create(getActivity(), R.layout.fragment_challenge_list);
        offset++;

        ///////////////// ACTIVITY FEED /////////////////

        // complete challenges (both people finished the race) involving one of your friends. Covers:
        // 1. You vs a friend races - to remind yourself of races you've completed;
        // 2. Friend vs friend races
        // 3. Friend vs other (friend of friend) races
        filteredNotifications = activityFilter(notifications);
        activityAdapter = ActivityAdapter.create(getActivity(), filteredNotifications);
        offset += filteredNotifications.size();

        ImmutableList<? extends StickyListHeadersAdapter> adapters =
                ImmutableList.of(
                        inboxListAdapter,
                        inboxEmptyAdapter,
                        verticalMissionListWrapperAdapter,
                        raceYourselfAdapter,
                        runListAdapter,
                        runnableEmptyAdapter,
                        automatchAdapter,
                        activityAdapter);

        compositeListAdapter = new HomeFeedCompositeListAdapter(
                getActivity(), android.R.layout.simple_list_item_1, adapters);

        stickyListView.setAdapter(compositeListAdapter);
        stickyListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // Prevent syncing while we scroll
                SyncHelper.getInstance(view.getContext()).requestStallFor(5000);
            }
        });

        stickyListView.getWrappedList().setOnItemClickListener(this);

        inboxListAdapter.setOnInboxChallengeAction(new ChallengeDetailView.OnInboxChallengeAction() {
            @Override
            public void onIgnore(ChallengeNotificationBean challengeNotificationBean) {
                Notification notif = Notification.get(challengeNotificationBean.getId());
                notif.deleted_at = new Date();
                notif.dirty = true;
                notif.save();
                Event.log(new Event.EventEvent("ignore_challenge").setChallengeId(challengeNotificationBean.getChallenge().getChallengeId()));

                clearSelectedChallenge();

                inboxListAdapter.remove(challengeNotificationBean);
                if (inboxListAdapter.isEmpty()) inboxEmptyAdapter.show();
                else inboxEmptyAdapter.hide();
                runListAdapter.setListOffset(inboxListAdapter.getCount() + 1 + inboxEmptyAdapter.getCount()); // update sub-list offsets
                inboxListAdapter.notifyDataSetChanged();

                compositeListAdapter.notifyDataSetChanged(); // why not, eh?
            }

            @Override
            public void onAccept(ChallengeNotificationBean challengeNotificationBean) {
                // TODO animate move. Sadly, I don't see any move methods - it's an add and a remove.
                Notification notif = Notification.get(challengeNotificationBean.getId());
                notif.setRead(true);
                challengeNotificationBean.setRead(true);
                Event.log(new Event.EventEvent("accept_challenge").setChallengeId(challengeNotificationBean.getChallenge().getChallengeId()));

                inboxListAdapter.remove(challengeNotificationBean);
                if (inboxListAdapter.isEmpty()) inboxEmptyAdapter.show();
                else inboxEmptyAdapter.hide();
                runListAdapter.setListOffset(inboxListAdapter.getCount() + 1 + inboxEmptyAdapter.getCount()); // update sub-list offsets
                runListAdapter.add(challengeNotificationBean);
                if (runListAdapter.isEmpty()) runnableEmptyAdapter.show();
                else runnableEmptyAdapter.hide();
                inboxListAdapter.notifyDataSetChanged();
                runListAdapter.notifyDataSetChanged();
                compositeListAdapter.notifyDataSetChanged();
            }
        });

        // Attach ChallengeVersusAnimator once challenge list is created
        ExpandableChallengeListAdapter cAdapter = getInboxListAdapter();
        ChallengeVersusAnimator cAnimator = new ChallengeVersusAnimator(getActivity(), cAdapter);
        versusAnimators.add(cAnimator);

        List<? extends ExpandCollapseListener> listeners =
                ImmutableList.of(new ChallengeSelector(cAdapter), cAnimator);
        ExpandCollapseListenerGroup listenerGroup = new ExpandCollapseListenerGroup(listeners);
        cAdapter.setExpandCollapseListener(listenerGroup);

        ExpandableChallengeListAdapter rAdapter = getRunListAdapter();
        ChallengeVersusAnimator rAnimator = new ChallengeVersusAnimator(getActivity(), rAdapter);
        versusAnimators.add(rAnimator);
        listeners = ImmutableList.of(new ChallengeSelector(rAdapter), rAnimator);
        listenerGroup = new ExpandCollapseListenerGroup(listeners);
        rAdapter.setExpandCollapseListener(listenerGroup);

        Typeface corben = Typeface.createFromAsset(activity.getAssets(), "corben_bold.ttf");
        vsTextview.setTypeface(corben);

        User player = User.get(AccessToken.get().getUserId());
        Picasso.with(getActivity()).load(player.getImage())
                .placeholder(R.drawable.default_profile_pic)
                .transform(new PictureUtils.CropCircle())
                .into(playerPic);

        rankIcon.setImageDrawable(getResources().getDrawable(UserBean.getRankDrawable(player.getRank())));

        playerName.setText(StringFormattingUtils.getForename(player.getName()));
    }

    @Click
    void runButton() {
        if (selectedChallenge == null) {
            // We choose from a few different possible messages to guide the user as to why they can't run yet!
            int messageId = R.string.race_btn_select_quickmatch;

            // For consistency with the UI, we fetch the challenges currently being displayed.
            List<ChallengeNotificationBean> challenges = getNotifications();

            for (ChallengeNotificationBean challenge : challenges) {
                if (challenge.isRunnableNow()) {
                    messageId = R.string.race_btn_select_opponent;
                    break;
                }
                if (challenge.isInbox())
                    messageId = R.string.race_btn_accept_challenge;
            }

            Toast.makeText(getActivity(), messageId, Toast.LENGTH_LONG).show();
            scrollToRunSection();
        }
        else if (selectedChallenge != null && selectedChallenge.getOpponentTrack() == null) {
            Toast.makeText(getActivity(), R.string.race_btn_still_loading, Toast.LENGTH_LONG).show();
        }
        else {
            Intent gameIntent = new Intent(getActivity(), GameActivity.class);
            gameIntent.putExtra("challenge", selectedChallenge);
            getActivity().startActivity(gameIntent);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        HomeFeedRowBean bean = compositeListAdapter.getItem(position);

        if (listener == null) {
            log.error("OnFragmentInteractionListener is null.");
        }
        else if (bean instanceof AutomatchBean) {
            listener.onQuickmatchSelect();
        }
        else if (bean instanceof RaceYourselfBean) {
            listener.raceYourself();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
        listener = (OnFragmentInteractionListener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        ((MobileApplication)getActivity().getApplication()).addCallback(
                SyncHelper.MESSAGING_TARGET_PLATFORM,
                SyncHelper.MESSAGING_METHOD_ON_SYNCHRONIZATION, challengeListRefreshHandler);
        ((MobileApplication)getActivity().getApplication()).addCallback(
                HomeFeedFragment.class.getSimpleName(), challengeListRefreshHandler);

        refreshLists();
    }

    @Override
    public void onPause() {
        super.onPause();
        ((MobileApplication)getActivity().getApplication()).removeCallback(
                SyncHelper.MESSAGING_TARGET_PLATFORM,
                SyncHelper.MESSAGING_METHOD_ON_SYNCHRONIZATION, challengeListRefreshHandler);
        ((MobileApplication)getActivity().getApplication()).removeCallback(
                HomeFeedFragment.class.getSimpleName(), challengeListRefreshHandler);
    }

    public void scrollToRunSection() {
        int idx = compositeListAdapter.getFirstPosition(raceYourselfAdapter.getClass());
        listView.setSelection(idx);
    }

    private void refreshLists() {
        notifications = ImmutableList.copyOf(sortChallengeNotifications(
                ChallengeNotificationBean.from(Notification.getNotificationsByType("challenge"))));

        List<ChallengeNotificationBean> refreshedInbox = inboxFilter(notifications);
        List<ChallengeNotificationBean> refreshedRun = runFilter(notifications);
        List<ChallengeNotificationBean> activityList = activityFilter(notifications);

        updateAdapters(refreshedInbox, refreshedRun, activityList);
    }

    @UiThread
    void updateAdapters(List<ChallengeNotificationBean> refreshedInbox,
                        List<ChallengeNotificationBean> refreshedRun,
                        List<ChallengeNotificationBean> activityList) {
        int offset = 0; // sub-list offset
        inboxListAdapter.mergeItems(refreshedInbox);
        inboxListAdapter.setListOffset(offset);
        offset += refreshedInbox.size();
        if (refreshedInbox.isEmpty()) inboxEmptyAdapter.show();
        else inboxEmptyAdapter.hide();
        offset += inboxEmptyAdapter.getCount();
        verticalMissionListWrapperAdapter.refresh();
        offset++; // vertical mission list
        offset++; // race yourself
        runListAdapter.mergeItems(refreshedRun);
        runListAdapter.setListOffset(offset);
        offset += refreshedRun.size();
        if (refreshedRun.isEmpty()) runnableEmptyAdapter.show();
        else runnableEmptyAdapter.hide();
        offset += runnableEmptyAdapter.getCount();
        offset++; // automatch
        // offset += activityList.size();
        activityAdapter.mergeItems(activityList);
        offset += activityList.size();

        compositeListAdapter.notifyDataSetChanged(); // Rebuild headers
    }

    @Override
    public void onFragmentInteraction(MissionBean mission, View view) {
        if (listener != null) listener.onFragmentInteraction(mission, view);
    }

    private class ChallengeListRefreshHandler implements MobileApplication.Callback<String> {
        @Override
        public boolean call(String message) {
            // Refresh list if sync succeeded or someone requested a refresh
            if (SyncHelper.MESSAGING_MESSAGE_SYNC_SUCCESS_FULL.equals(message)
                    || SyncHelper.MESSAGING_MESSAGE_SYNC_SUCCESS_PARTIAL.equals(message)
                    || MESSAGING_MESSAGE_REFRESH.equals(message)) {

                refreshLists();
            }
            return false; // recurring
        }
    }

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(ChallengeNotificationBean challengeNotificationBean);
        public void onFragmentInteraction(MissionBean missionBean, View view);
        public void raceYourself();
        public void onQuickmatchSelect();
    }

    private class ChallengeSelector implements ExpandCollapseListener {
        private final ExpandableChallengeListAdapter adapter;

        public ChallengeSelector(ExpandableChallengeListAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onItemExpanded(int position) {
            // NOTE: position is local to child adapter. Cannot use composite.getItem(position)
            ChallengeNotificationBean notificationBean = adapter.getItem(position);
            ChallengeDetailBean detailBean = new ChallengeDetailBean();
            User player = User.get(AccessToken.get().getUserId());
            final UserBean playerBean = new UserBean(player);
            detailBean.setPlayer(playerBean);
            detailBean.setOpponent(notificationBean.getOpponent());
            detailBean.setChallenge(notificationBean.getChallenge());
            detailBean.setNotificationId(notificationBean.getId());

            // Set selected
            setSelectedChallenge(detailBean);
            // Fetch track in background
            retrieveChallengeDetails(detailBean);
        }

        @Override
        public void onItemCollapsed(int position) {
            // do nothing - keep challenge selected until user picks another challenge.
        }
    }

    @Background
    public void retrieveChallengeDetails(@NonNull ChallengeDetailBean challengeDetailBean) {
        try {
            Challenge challenge = SyncHelper.getChallenge(challengeDetailBean.getChallenge().getDeviceId(),
                    challengeDetailBean.getChallenge().getChallengeId());

            if (challenge == null) {
                networkError("Challenge has been removed from the server!");
                if (selectedChallenge == null ||
                        selectedChallenge.getChallenge().equals(challengeDetailBean.getChallenge()))
                    clearSelectedChallenge();
                return;
            }

            log.info(String.format("Challenge <%d,%d>- checking attempts, there are %d attempts",
                    challenge.device_id, challenge.challenge_id, challenge.getAttempts().size()));

            GameConfiguration gameConfiguration = new GameConfiguration.GameStrategyBuilder(
                    GameConfiguration.GameType.TIME_CHALLENGE).targetTime(challenge.duration * 1000).build();
            for (Challenge.ChallengeAttempt attempt : challenge.getAttempts()) {
                if (attempt.user_id == challengeDetailBean.getOpponent().getId()) {
                    log.info("Challenge - checking attempts, found opponent " + attempt.user_id);
                    Track opponentTrack = SyncHelper.getTrack(attempt.track_device_id, attempt.track_id);
                    if (opponentTrack != null) {
                        TrackSummaryBean opponentTrackBean = new TrackSummaryBean(opponentTrack, gameConfiguration);
                        challengeDetailBean.setOpponentTrack(opponentTrackBean);
                    } else {
                        networkError("Opponent's track does not exist on server!");
                        if (selectedChallenge == null || selectedChallenge.getChallenge().equals(challengeDetailBean.getChallenge()))
                            clearSelectedChallenge();
                        return;
                    }
                    break;
                }
            }
            if (challengeDetailBean.getOpponentTrack() == null) {
                log.warn("No track associated with challenge!");
                networkError("Opponent has not attempted challenge yet!");
                if (selectedChallenge == null ||
                        selectedChallenge.getChallenge().equals(challengeDetailBean.getChallenge()))
                    clearSelectedChallenge();
                return;
            }

            if (selectedChallenge == null ||
                    selectedChallenge.getChallenge().equals(challengeDetailBean.getChallenge()))
                setSelectedChallenge(challengeDetailBean);

        } catch (SyncHelper.CouldNotFetchException e) {
            networkError("Failed to fetch challenge data from network!");
            if (selectedChallenge == null ||
                    selectedChallenge.getChallenge().equals(challengeDetailBean.getChallenge()))
                clearSelectedChallenge();
        }
    }

    @UiThread
    public void networkError(String error) {
        Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
    }

    @UiThread
    public void setSelectedChallenge(@NonNull ChallengeDetailBean selectedChallenge) {
        this.selectedChallenge = selectedChallenge;
        inboxListAdapter.notifyDataSetChanged();
        runListAdapter.notifyDataSetChanged();
    }

    @UiThread
    public void clearSelectedChallenge() {
        this.selectedChallenge = null;
        for (ChallengeVersusAnimator animator : versusAnimators)
            animator.cancel();
        // Set versus opponent name immediately so the race now button makes sense
        final ViewGroup rl = (ViewGroup) getActivity().findViewById(R.id.activity_home);
        final ImageView opponent = (ImageView)rl.findViewById(R.id.opponentPic);
        final TextView opponentName = (TextView)rl.findViewById(R.id.opponentName);
        final ImageView opponentRank = (ImageView)rl.findViewById(R.id.opponentRank);
        opponent.setImageDrawable(getResources().getDrawable(R.drawable.default_profile_pic));
        opponentName.setText("- ? -");
        opponentRank.setVisibility(View.INVISIBLE);
    }

    private List<ChallengeNotificationBean> sortChallengeNotifications(List<ChallengeNotificationBean> beans) {
        Collections.sort(beans, new Comparator<ChallengeNotificationBean>() {
            @Override
            public int compare(ChallengeNotificationBean lhs, ChallengeNotificationBean rhs) {
                // Newest first - so invert order of arguments in compareTo: rhs - lhs.
                if (lhs.getUpdatedAt() != null && rhs.getUpdatedAt() != null)
                    return rhs.getUpdatedAt().compareTo(lhs.getUpdatedAt());
                return rhs.getId() - lhs.getId();
            }
        });
        return beans; // allows method to be chained
    }
}
