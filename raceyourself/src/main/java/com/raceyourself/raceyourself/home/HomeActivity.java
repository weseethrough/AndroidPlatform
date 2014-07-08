package com.raceyourself.raceyourself.home;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.model.GraphUser;
import com.google.common.collect.ImmutableMap;
import com.raceyourself.platform.auth.AuthenticationActivity;
import com.raceyourself.platform.gpstracker.Helper;
import com.raceyourself.platform.models.Notification;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.BaseActivity;
import com.raceyourself.raceyourself.matchmaking.ChooseFitnessActivity;

import java.io.IOException;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HomeActivity extends BaseActivity implements ActionBar.TabListener,
        FriendFragment.OnFragmentInteractionListener, ChallengeFragment.OnFragmentInteractionListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    private HomePagerAdapter pagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager viewPager;

    private boolean paused;

    private View loginButton;

    private UiLifecycleHelper facebookUiHelper;
    private Session.StatusCallback callback = new Session.StatusCallback() {
        @Override
        public void call(Session session, SessionState state, Exception exception) {
            onSessionStateChange(session, state, exception);
        }
    };

    private void onSessionStateChange(Session session, SessionState state, Exception exception) {
        if (!paused) {
            // check for the OPENED state instead of session.isOpened() since for the
            // OPENED_TOKEN_UPDATED state, the selection fragment should already be showing.
            if (state.equals(SessionState.OPENED)) {
                showFacebookLogin(false);
                final String accessToken = session.getAccessToken();

                log.debug("onSessionStateChange() - FB session is open");

                Request request = Request.newMeRequest(session, new Request.GraphUserCallback() {
                    @Override
                    public void onCompleted(GraphUser user, Response response) {
                        if (user != null) {
                            log.debug("onCompleted() - got 'me'!");

                            final String userId = user.getId();

                            new AsyncTask<Void, Void, Void>() {
                                private IOException e;

                                @Override
                                protected Void doInBackground(Void... params) {
                                    log.debug("doInBackground() - hopefully not on a UI thread now?");
                                    try {
                                        AuthenticationActivity.linkProvider("facebook", userId, accessToken);
                                    } catch (IOException e) {
                                        this.e = e;
                                    }
                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Void result) {
                                    if (e != null)
                                        log.error("Unable to link Facebook provider", e);
                                }
                            }.execute();
                        }
                        else
                            throw new IllegalStateException("TODO: error handling (Facebook me request failed");
                    }
                });
                Request.executeBatchAsync(request);
            }
            else if (state.isClosed())
                showFacebookLogin(true);
            else
                throw new IllegalStateException("Unknown FB Session state - neither open nor closed? Is Schroedinger's cat both alive and dead?");
        }
    }

    private void showFacebookLogin(boolean show) {
        Button fbButton = (Button) findViewById(R.id.facebook_connect_button);
        fbButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        facebookUiHelper = new UiLifecycleHelper(this, callback);
        facebookUiHelper.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the
        // primary sections of the activity.
        pagerAdapter = new HomePagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        viewPager = (ViewPager) findViewById(R.id.home_pager);
        viewPager.setAdapter(pagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(pagerAdapter.getPageTitle(i))
                            .setTabListener(this)
            );
        }

        Button raceNowButton = (Button) findViewById(R.id.race_now_quickmatch);
        raceNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(HomeActivity.this, ChooseFitnessActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    public void onPause() {
        super.onPause();
        facebookUiHelper.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        facebookUiHelper.onResume();
        paused = false;

        /*
        FIXME: the following lines are adapted from FB's Android API demo 'Scrumptious', which
        has a MainActivity that extends FragmentActivity. MainActivity overrides
        onResumeFragments()... but we don't have that here, as this class directly extends
        Activity (the ViewPager holds the Fragment references instead). As such this code sits here
        for the moment, but it may result in lifecycle sequencing issues.
         */
        Session session = Session.getActiveSession();
        if (session != null && session.isOpened()) {
            // if the session is already open, try to show the selection fragment
            showFacebookLogin(false);
        } else {
            showFacebookLogin(true);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        facebookUiHelper.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        facebookUiHelper.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onFragmentInteraction(UserBean user) {
        log.info("Friend selected: {}", user.getId());

        if (user == null)
            throw new IllegalArgumentException("null friend");
        if (user.getId() <= 0)
            throw new IllegalArgumentException("Friend's ID must be positive.");

        UserBean.JoinStatus status = user.getJoinStatus();
        if (status == UserBean.JoinStatus.NOT_MEMBER) {
            inviteFriend(user);
        } else if (status == UserBean.JoinStatus.INVITE_SENT) {
            // no action defined at present. maybe send reminder?
        }
        else if (status.isMember()) {
            challengeFriend(user);
        }
        else
            throw new Error("Unrecognised UserBean.JoinStatus: " + status);
    }

    private void challengeFriend(UserBean user) {
        Helper.queueAction(String.format("{\"action\":\"challenge\", \"target\":%d,\n" +
                "            \"taunt\" : \"Try beating my track!\",\n" +
                "            \"challenge\" : {\n" +
                "                    \"distance\": %d,\n" +
                "                    \"duration\": %d,\n" +
                "                    \"public\": true,\n" +
                "                    \"start_time\": null,\n" +
                "                    \"stop_time\": null,\n" +
                "                    \"type\": \"duration\"\n" +
                "            }}", user.getId(), 5, 1000));
    }

    private void inviteFriend(UserBean user) {

    }

    @Override
    public void onFragmentInteraction(ChallengeNotificationBean challengeNotification) {
        log.info("Challenge selected: {}", challengeNotification.getId());
        Notification.get(challengeNotification.getId()).setRead(true);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class HomePagerAdapter extends FragmentPagerAdapter {

        private Map<Integer, Fragment> fragments =
                new ImmutableMap.Builder<Integer, Fragment>()
                        .put(0, new ChallengeFragment())
                        .put(1, new FriendFragment())
                        .build();
        private Map<Integer, String> fragmentTitles =
                new ImmutableMap.Builder<Integer, String>()
                        .put(0, getString(R.string.title_challenges_page))
                        .put(1, getString(R.string.title_friends_page))
                        .build();

        public HomePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment = fragments.get(position);
            if (fragment != null)
                return fragment;
            throw new IllegalArgumentException(String.format("No such tab index: %d", position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            String title = fragmentTitles.get(position);
            if (title != null)
                return title;
            throw new IllegalArgumentException(String.format("No such tab index: %d", position));
        }
    }

}
