package com.raceyourself.raceyourself.home.feed;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;
import android.widget.TextView;

import com.raceyourself.platform.gpstracker.SyncHelper;
import com.raceyourself.platform.models.AccessToken;
import com.raceyourself.platform.models.Challenge;
import com.raceyourself.platform.models.Track;
import com.raceyourself.platform.models.User;
import com.raceyourself.platform.utils.Format;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.util.StringFormattingUtils;
import com.raceyourself.raceyourself.home.UserBean;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Duncan on 15/07/2014.
 */
@Slf4j
@EViewGroup(R.layout.fragment_inbox_expanded)
public class ChallengeDetailView extends ScrollView {
    private Context context;

    @ViewById
    TextView trackDistance;

    @ViewById
    TextView ascentText;

    @ViewById
    TextView descentText;

    @ViewById
    TextView trackLength;

    public ChallengeDetailView(Context context) {
        super(context);
        this.context = context;
    }

    public ChallengeDetailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        this.context = context;
    }

    public ChallengeDetailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(ChallengeNotificationBean currentChallenge) {
        User player = SyncHelper.getUser(AccessToken.get().getUserId());
        final UserBean playerBean = new UserBean(player);

        final ChallengeDetailBean activeChallengeFragment = new ChallengeDetailBean();
        activeChallengeFragment.setOpponent(currentChallenge.getUser());
        activeChallengeFragment.setPlayer(playerBean);
        activeChallengeFragment.setChallenge(currentChallenge.getChallenge());

        String duration = StringFormattingUtils.ACTIVITY_PERIOD_FORMAT.print(
                activeChallengeFragment.getChallenge().getDuration().toPeriod());
        trackLength.setText(duration);

        retrieveChallengeDetail(activeChallengeFragment, playerBean);

        // TODO will need the following code for the 'Run!' items... but not here (accept is a different action)
//        final Button raceNowBtn = (Button) findViewById(R.id.raceNowBtn);
//        raceNowBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Intent gameIntent = new Intent(context, GameActivity.class);
//                gameIntent.putExtra("challenge", activeChallengeFragment);
//                context.startActivity(gameIntent);
//            }
//        });
    }

    @Background
    void retrieveChallengeDetail(@NonNull ChallengeDetailBean activeChallengeFragment,
                                 @NonNull UserBean playerBean) {
        log.debug("retrieveChallengeDetail");

        ChallengeDetailBean challengeDetailBean = new ChallengeDetailBean();
        Challenge challenge = SyncHelper.getChallenge(
                activeChallengeFragment.getChallenge().getDeviceId(),
                activeChallengeFragment.getChallenge().getChallengeId());
        challengeDetailBean.setChallenge(new ChallengeBean(challenge));
        Boolean playerFound = false;
        Boolean opponentFound = false;
        if (challenge != null) {
            for (Challenge.ChallengeAttempt attempt : challenge.getAttempts()) {
                if (attempt.user_id == playerBean.getId() && !playerFound) {
                    playerFound = true;
                    Track playerTrack = SyncHelper.getTrack(attempt.track_device_id, attempt.track_id);
                    activeChallengeFragment.setPlayerTrack(new TrackSummaryBean(playerTrack));
                } else if (attempt.user_id == activeChallengeFragment.getOpponent().getId() && !opponentFound) {
                    opponentFound = true;
                    Track opponentTrack = SyncHelper.getTrack(attempt.track_device_id, attempt.track_id);
                    activeChallengeFragment.setOpponentTrack(new TrackSummaryBean(opponentTrack));
                }
                if (playerFound && opponentFound) {
                    break;
                }
            }
        }
        drawChallengeDetail(activeChallengeFragment);
    }

    @UiThread
    void drawChallengeDetail(@NonNull ChallengeDetailBean activeChallengeFragment) {
        log.debug("drawChallengeDetail");

        TrackSummaryBean opponentTrack = activeChallengeFragment.getOpponentTrack();

        if(opponentTrack != null) {
            trackDistance.setText(Format.twoDp(opponentTrack.getDistanceRan()) + " km");
            ascentText.setText(Format.twoDp(opponentTrack.getTotalUp()) + " km");
            descentText.setText(Format.twoDp(opponentTrack.getTotalDown()) + " km");
        }
    }
}