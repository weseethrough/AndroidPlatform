package com.raceyourself.raceyourself.matchmaking;

import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.raceyourself.platform.models.AccessToken;
import com.raceyourself.platform.models.Track;
import com.raceyourself.platform.models.User;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.util.PictureUtils;
import com.raceyourself.raceyourself.game.GameConfiguration;
import com.raceyourself.raceyourself.home.HomeActivity;
import com.raceyourself.raceyourself.home.HomeActivity_;
import com.raceyourself.raceyourself.home.UserBean;
import com.raceyourself.raceyourself.home.feed.ChallengeBean;
import com.raceyourself.raceyourself.home.feed.ChallengeDetailBean;
import com.raceyourself.raceyourself.home.feed.TrackSummaryBean;
import com.raceyourself.raceyourself.home.sendchallenge.SetChallengeView;
import com.squareup.picasso.Picasso;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.annotations.ViewById;

import java.util.SortedMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Amerigo on 25/07/2014.
 */
@Slf4j
@EViewGroup(R.layout.activity_select_duration)
public class RaceYourselfDurationView extends DurationView {

    @ViewById
    TextView lengthWarning;

    @ViewById
    Button findBtn;

    @ViewById(R.id.playerProfilePic)
    @Getter
    ImageView opponentProfilePic;

    @ViewById(R.id.furthestRunText)
    TextView furthestRunBeforeDurationText;

    ChallengeDetailBean challengeDetail;

    private SortedMap<Integer, Pair<Track, SetChallengeView.MatchQuality>> availableOwnTracksMap;

    @AfterViews
    public void afterRaceYourselfViews() {
        furthestRunBeforeDurationText.setText(R.string.duration_description_raceyourself);

        TextView furthestRunAfterTime = (TextView)findViewById(R.id.furthestRunAfterTime);
        furthestRunAfterTime.setVisibility(View.VISIBLE);

        lengthWarning.setVisibility(View.VISIBLE);

        findBtn.setText(R.string.raceyourself_button);
    }

    public RaceYourselfDurationView(Context context) {
        super(context);

        availableOwnTracksMap = SetChallengeView.populateAvailableUserTracksMap();
    }

    @Override
    public void checkRaceYourself() {
        SetChallengeView.MatchQuality quality = availableOwnTracksMap.get(duration).second;

            // TODO jodatime...
            String qualityWarning = quality.getMessageId() == null ? "" :
                    String.format(context.getString(quality.getMessageId()), duration + " mins");

            lengthWarning.setText(qualityWarning);

            final boolean enable = quality != SetChallengeView.MatchQuality.TRACK_TOO_SHORT;
            // Disable send button if no runs recorded that are long enough.
            // Having a run that's too long is fine - we can truncate it.
            findBtn.setEnabled(enable);
            findBtn.setClickable(enable);
    }

    @Override
    public void onDistanceClick() {
        User player = User.get(AccessToken.get().getUserId());
        UserBean playerBean = new UserBean(player);

        GameConfiguration gameConfiguration = new GameConfiguration.GameStrategyBuilder(
                GameConfiguration.GameType.TIME_CHALLENGE).targetTime(duration*60*1000).countdown(2999).build();

        // TODO refactor to avoid this dependency on SetChallengeView.
        Pair<Track,SetChallengeView.MatchQuality> p = availableOwnTracksMap.get(duration);

        TrackSummaryBean opponentTrack = new TrackSummaryBean(p.first, gameConfiguration);

        ChallengeBean challengeBean = new ChallengeBean(null);
        challengeBean.setType("duration");
        challengeBean.setChallengeGoal(duration * 60);
        challengeBean.setPoints(20000);

        challengeDetail = new ChallengeDetailBean();
        challengeDetail.setOpponent(playerBean);
        challengeDetail.setPlayer(playerBean);
        challengeDetail.setOpponentTrack(opponentTrack);
        challengeDetail.setChallenge(challengeBean);
    }

    @Override
    public ChallengeDetailBean getChallengeDetail() {
        return challengeDetail;
    }

    @Override
    public String getFurthestRunText() {
        StringBuilder builder = new StringBuilder(super.getFurthestRunText());
        builder.append("?");
        return builder.toString();
    }
}
