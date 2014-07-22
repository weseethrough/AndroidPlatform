package com.raceyourself.raceyourself.matchmaking;

import android.animation.Animator;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.raceyourself.platform.auth.AuthenticationActivity;
import com.raceyourself.platform.gpstracker.SyncHelper;
import com.raceyourself.platform.models.AccessToken;
import com.raceyourself.platform.models.AutoMatches;
import com.raceyourself.platform.models.Track;
import com.raceyourself.platform.models.User;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.util.PictureUtils;
import com.raceyourself.raceyourself.base.util.StringFormattingUtils;
import com.raceyourself.raceyourself.home.HomeActivity;
import com.raceyourself.raceyourself.home.UserBean;
import com.raceyourself.raceyourself.home.feed.ChallengeBean;
import com.raceyourself.raceyourself.home.feed.ChallengeDetailBean;
import com.raceyourself.raceyourself.home.feed.ChallengeNotificationBean;
import com.raceyourself.raceyourself.home.feed.TrackSummaryBean;
import com.squareup.picasso.Picasso;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by Amerigo on 17/07/2014.
 */
@Slf4j
public class MatchmakingPopupController implements SeekBar.OnSeekBarChangeListener {
    int duration;

    TextView durationTextView;
    TextView furthestRunTextView;

    HomeActivity homeActivity;

    PopupWindow matchmakingFitnessPopup;
    PopupWindow matchmakingDistancePopup;
    PopupWindow matchmakingFindingPopup;

    LayoutInflater inflater;

    String fitness;

    TextView matchingText;
    TextView searchingText;
    TextView matrixText;
    TextView foundText;

    ImageView heartIcon;
    ImageView globeIcon;
    ImageView wandIcon;
    ImageView tickIcon;

    Animation translateRightAnim;
    Animation rotationAnim;

    Drawable checkmarkIconDrawable;
    Drawable loadingIconDrawable;

    Button raceButton;
    Button searchAgainButton;

    User opponent;

    TextView opponentNameText;
    ImageView opponentProfilePic;

    ChallengeDetailBean challengeDetail;

    int animationCount = 0;

    public MatchmakingPopupController(){}

    public MatchmakingPopupController(HomeActivity homeActivity) {
        this.homeActivity = homeActivity;
        inflater = LayoutInflater.from(homeActivity);
    }

    public void displayFitnessPopup() {
        View popupView = inflater.inflate(R.layout.activity_choose_fitness, null);
        matchmakingFitnessPopup = new PopupWindow(popupView);
        matchmakingFitnessPopup.setWindowLayoutMode(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        matchmakingFitnessPopup.showAtLocation(
                homeActivity.getWindow().getDecorView().findViewById(android.R.id.content), Gravity.CENTER, 0, 0);
    }

    public void onFitnessBtn(View view) {
        fitness = "";
        switch(view.getId()) {
            case R.id.outOfShape:
                fitness = "out of shape";
                break;
            case R.id.averageBtn:
                fitness = "average";
                break;
            case R.id.athleticBtn:
                fitness = "athletic";
                break;
            case R.id.eliteBtn:
                fitness = "elite";
                break;
            default:
                log.error("id not found");
                return;
        }
        final String finalFitness = fitness;
        Thread updateUserThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthenticationActivity.editUser(new AuthenticationActivity.UserDiff().profile("running_fitness", finalFitness));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        updateUserThread.start();

        displayDistancePopup();
        matchmakingFitnessPopup.dismiss();
    }

    public boolean isDisplaying() {
        if(matchmakingFindingPopup != null &&  matchmakingFindingPopup.isShowing()) {
            displayDistancePopup();
            matchmakingFindingPopup.dismiss();
            return true;
        } else if(matchmakingDistancePopup != null &&matchmakingDistancePopup.isShowing()) {
            displayFitnessPopup();
            matchmakingDistancePopup.dismiss();
            return true;
        } else if(matchmakingFitnessPopup != null && matchmakingFitnessPopup.isShowing()) {
            matchmakingFitnessPopup.dismiss();
            return true;
        }

        return false;
    }

    public void onMatchClick() {
        displayFindingPopup();
        matchmakingDistancePopup.dismiss();
    }

    public void displayDistancePopup() {
        View durationView = inflater.inflate(R.layout.activity_select_duration, null);
        matchmakingDistancePopup = new PopupWindow(durationView);
        matchmakingDistancePopup.setWindowLayoutMode(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        durationTextView = (TextView)durationView.findViewById(R.id.duration);
        furthestRunTextView = (TextView)durationView.findViewById(R.id.furthestRunNumber);

        SeekBar seekBar = (SeekBar)durationView.findViewById(R.id.matchmaking_distance_bar);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setMax(30);
        User user = User.get(AccessToken.get().getUserId());

        ImageView playerImage = (ImageView) durationView.findViewById(R.id.playerProfilePic);
        String url = user.getImage();
        Picasso.with(homeActivity).load(url).placeholder(R.drawable.default_profile_pic).transform(new PictureUtils.CropCircle()).into(playerImage);

        Button findBtn = (Button) durationView.findViewById(R.id.findBtn);
        findBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMatchClick();
            }
        });

        matchmakingDistancePopup.showAtLocation(
                homeActivity.getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
    }

    public void displayFindingPopup() {
        animationCount = 0;
        View findingView = inflater.inflate(R.layout.activity_matchmaking_finding, null);

        matchingText = (TextView)findingView.findViewById(R.id.matchingText);
        searchingText = (TextView)findingView.findViewById(R.id.searchingText);
        matrixText = (TextView)findingView.findViewById(R.id.matrixText);
        foundText = (TextView)findingView.findViewById(R.id.matchedText);

        heartIcon = (ImageView)findingView.findViewById(R.id.heartIcon);
        globeIcon = (ImageView)findingView.findViewById(R.id.globeIcon);
        wandIcon = (ImageView)findingView.findViewById(R.id.wandIcon);
        tickIcon = (ImageView)findingView.findViewById(R.id.tickIcon);

        translateRightAnim = AnimationUtils.loadAnimation(homeActivity, R.anim.matched_text_anim);
        rotationAnim = AnimationUtils.loadAnimation(homeActivity, R.anim.rotating_icon_anim);

        checkmarkIconDrawable = homeActivity.getResources().getDrawable(R.drawable.icon_checkmark);
        loadingIconDrawable = homeActivity.getResources().getDrawable(R.drawable.icon_loading);

        raceButton = (Button)findingView.findViewById(R.id.quickmatch_ok_button);
        searchAgainButton = (Button)findingView.findViewById(R.id.searchAgainBtn);

        opponentNameText = (TextView)findingView.findViewById(R.id.opponentName);
        opponentProfilePic = (ImageView)findingView.findViewById(R.id.opponentProfilePic);

        User user = User.get(AccessToken.get().getUserId());
        String url = user.getImage();

        final ImageView playerImage = (ImageView)findingView.findViewById(R.id.playerProfilePic);
        Picasso
            .with(homeActivity)
            .load(url)
            .placeholder(R.drawable.default_profile_pic)
            .transform(new PictureUtils.CropCircle())
            .into(playerImage);

        List<Track> trackList = AutoMatches.getBucket(fitness, duration);
        log.info(trackList.size() + " appropriate tracks found");
        if (trackList.size() == 0) {
            log.error("No tracks found for this distance / fitness level. Please try another.");
//            Toast toast = new Toast(homeActivity);
            Toast.makeText(homeActivity, "No tracks found for this distance / fitness level. Please try another.",
                    Toast.LENGTH_SHORT).show();
        }

        // choose random track from list
        Random random = new Random();
        int trackNumber = random.nextInt(trackList.size());
        final Track selectedTrack = trackList.get(trackNumber);

        log.info("Matched track " + selectedTrack.getId() + ", distance: " + selectedTrack.getDistance() + "m, pace: " +
                selectedTrack.getPace() + " min/km, by user " + selectedTrack.user_id);

        // background thread to pull chosen opponent's details from server
        ExecutorService pool = Executors.newFixedThreadPool(1);
        final Future<User> futureUser = pool.submit(new Callable<User>() {
            @Override
            public User call() throws Exception {
                return SyncHelper.get("users/" + selectedTrack.user_id, User.class);
            }
        });

        // start the funky matching animations
        translateRightAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                switch(animationCount) {
                    case 0:
                        startImageAnimation(heartIcon);
                        break;

                    case 1:
                        startImageAnimation(globeIcon);
                        break;

                    case 2:
                        startImageAnimation(wandIcon);
                        break;

                    case 3:
                        startImageAnimation(tickIcon);
                        break;
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        rotationAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                switch(animationCount) {
                    case 0:
                        endImageAnimation(heartIcon, checkmarkIconDrawable, searchingText);
                        break;
                    case 1:
                        endImageAnimation(globeIcon, checkmarkIconDrawable, matrixText);
                        break;
                    case 2:
                        endImageAnimation(wandIcon, checkmarkIconDrawable, foundText);
                        break;
                    case 3:
                        tickIcon.setImageDrawable(checkmarkIconDrawable);
                        raceButton.setVisibility(View.VISIBLE);
                        searchAgainButton.setVisibility(View.VISIBLE);
                        try {
                            opponent = futureUser.get();
                            opponentNameText.setText(StringFormattingUtils.getForename(opponent.name));
                            Picasso.with(homeActivity).load(opponent.getImage())
                                    .placeholder(R.drawable.default_profile_pic)
                                    .transform(new PictureUtils.CropCircle())
                                    .into(opponentProfilePic);

                            UserBean opponentBean = new UserBean(opponent);
                            challengeDetail.setOpponent(opponentBean);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                        break;
                }
                if(animationCount < 3) {
                    animationCount++;
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        matchingText.startAnimation(translateRightAnim);

        challengeDetail = new ChallengeDetailBean();

        UserBean player = new UserBean(user);
        challengeDetail.setPlayer(player);

        TrackSummaryBean opponentTrack = new TrackSummaryBean(selectedTrack);
        challengeDetail.setOpponentTrack(opponentTrack);

        ChallengeBean challengeBean = new ChallengeBean(null);
        challengeBean.setType("duration");
        challengeBean.setChallengeGoal(duration * 60);
        challengeDetail.setChallenge(challengeBean);

        challengeDetail.setPoints(20000);

        if(matchmakingFindingPopup != null && matchmakingFindingPopup.isShowing()) matchmakingFindingPopup.dismiss();

        matchmakingFindingPopup = new PopupWindow(findingView);

        matchmakingFindingPopup.setAnimationStyle(R.style.Animation);

        matchmakingFindingPopup.setWindowLayoutMode(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        matchmakingFindingPopup.showAtLocation(homeActivity.getWindow().getDecorView().getRootView(),
                Gravity.CENTER, 0, 0);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        int stepSize = 6;
        progress = (Math.round(progress/ stepSize))* stepSize;
        seekBar.setProgress(progress);
        duration = ((progress / stepSize) + 1) * 5;
        if(duration == 0) {
            duration = 5;
        }
        durationTextView.setText(duration + "");
        furthestRunTextView.setText(" " + duration + "mins?");
    }

    public void onRaceClick() {
        homeActivity.getPagerAdapter().getHomeFeedFragment().setSelectedChallenge(challengeDetail);
        TextView opponentName = (TextView) homeActivity.findViewById(R.id.opponentName);
        opponentName.setText(StringFormattingUtils.getForename(challengeDetail.getOpponent().getName()));

        // Clone profile image into root layout
        int[] location = new int[2];
        opponentProfilePic.getLocationOnScreen(location);

        final ViewGroup rl = (ViewGroup) homeActivity.findViewById(R.id.activity_home);
        int[] parent_location = new int[2];
        rl.getLocationOnScreen(parent_location);

        RelativeLayout.LayoutParams cp = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        ViewGroup.LayoutParams pp = opponentProfilePic.getLayoutParams();
        cp.width = pp.width;
        cp.height = pp.height;
        final ImageView clone = new ImageView(rl.getContext());
        clone.setImageDrawable(opponentProfilePic.getDrawable());
        clone.setScaleType(opponentProfilePic.getScaleType());
        clone.setLayoutParams(cp);
        clone.setX(location[0] - parent_location[0]);
        clone.setY(location[1] - parent_location[1]);
        rl.addView(clone);

        // Animate to opponent versus location
        final ImageView opponent = (ImageView)rl.findViewById(R.id.opponentPic);
        final ImageView opponentRank = (ImageView)rl.findViewById(R.id.opponentRank);
        final Drawable rankDrawable = homeActivity.getResources().getDrawable(challengeDetail.getOpponent().getRankDrawable());
        opponent.getLocationOnScreen(location);
        clone.animate().x(location[0] - parent_location[0]).y(location[1] - parent_location[1]).setDuration(1500).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                opponent.setImageDrawable(clone.getDrawable());
                opponentRank.setImageDrawable(rankDrawable);
                opponentRank.setVisibility(View.VISIBLE);
                rl.removeView(clone);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                opponent.setImageDrawable(clone.getDrawable());
                opponentRank.setImageDrawable(rankDrawable);
                opponentRank.setVisibility(View.VISIBLE);
                rl.removeView(clone);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        matchmakingFindingPopup.dismiss();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void startImageAnimation(ImageView imageView) {
        imageView.setImageDrawable(loadingIconDrawable);
        imageView.setVisibility(View.VISIBLE);
        imageView.startAnimation(rotationAnim);

    }

    public void endImageAnimation(ImageView imageView, Drawable drawable, TextView textView) {
        imageView.setImageDrawable(drawable);
        textView.setVisibility(View.VISIBLE);
        textView.startAnimation(translateRightAnim);
    }
}
