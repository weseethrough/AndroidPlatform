package com.raceyourself.raceyourself.home.feed;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.raceyourself.platform.gpstracker.SyncHelper;
import com.raceyourself.platform.models.AccessToken;
import com.raceyourself.platform.models.User;
import com.raceyourself.raceyourself.R;
import com.raceyourself.raceyourself.base.util.PictureUtils;
import com.raceyourself.raceyourself.base.util.StringFormattingUtils;
import com.raceyourself.raceyourself.home.UserBean;
import com.squareup.picasso.Picasso;

import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EViewGroup;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.joda.time.DateTime;
import org.joda.time.Period;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Duncan on 15/07/2014.
 */
@Slf4j
@EViewGroup(R.layout.fragment_inbox_collapsed)
public class ChallengeTitleView extends LinearLayout {

    private Context context;

    // TODO nothing in design for this yet!
//    @ViewById(R.id.challenge_notification_duration)
//    TextView durationView;

    @ViewById(R.id.challenge_notification_profile_pic)
    ImageView opponentProfilePic;

    @ViewById
    ImageView rankIcon;

    @ViewById(R.id.challenge_notification_challenger_name)
    TextView opponentName;

    @ViewById(R.id.challenge_notification_challenge_subtitle)
    TextView subtitle;

    private Integer notificationId = null;

    public ChallengeTitleView(Context context) {
        super(context);
        this.context = context;
    }

    public ChallengeTitleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        this.context = context;
    }

    public ChallengeTitleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bind(ChallengeNotificationBean notif) {
        if (notif instanceof AutomatchBean) {
            opponentProfilePic.setImageResource(R.drawable.icon_automatch);
            opponentName.setText(getContext().getString(R.string.home_feed_quickmatch_title));
            subtitle.setText(getContext().getString(R.string.home_feed_quickmatch_subtitle));
            rankIcon.setVisibility(View.GONE);
        }
        else if (notif instanceof RaceYourselfBean) {
            User player = User.get(AccessToken.get().getUserId());
            UserBean playerBean = new UserBean(player);
            Picasso.with(context)
                    .load(player.getImage())
                    .placeholder(R.drawable.default_profile_pic)
                    .transform(new PictureUtils.CropCircle())
                    .into(opponentProfilePic);
            opponentName.setText(getContext().getString(R.string.home_feed_raceyourself_title));
            subtitle.setText(getContext().getString(R.string.home_feed_raceyourself_subtitle));
            drawRankIcon(playerBean);
        }
        else {
            notificationId = notif.getId();
            ChallengeBean chal = notif.getChallenge(); // TODO avoid cast - more generic methods in ChallengeBean? 'limit' and 'goal'?

            if (notif.getOpponent().isPlaceHolder()) retrieveUsers(notif);
            else drawTitle(notif);

            if (notif.isRunnableNow()) {
                DateTime expiry = notif.getExpiry();

                String expiryStr;
                if (expiry.isBeforeNow())
                    expiryStr = context.getString(R.string.challenge_expired);
                else {
                    String period = StringFormattingUtils.TERSE_PERIOD_FORMAT.print(new Period(new DateTime(), expiry));
                    String expiryRes = context.getString(R.string.challenge_expiry);
                    expiryStr = String.format(expiryRes, period);
                }
                subtitle.setText(expiryStr);
            }
        }
    }

    @Background
    void retrieveUsers(ChallengeNotificationBean challengeNotificationBean) {
        if (notificationId != challengeNotificationBean.getId()) return; // view has been recycled
        try {
            User actualUser = SyncHelper.getUser(challengeNotificationBean.getOpponent().getId());
            if (actualUser != null) {
                UserBean user = challengeNotificationBean.getOpponent();
                user.setName(actualUser.getName());
                user.setShortName(StringFormattingUtils.getForenameAndInitial(user.getName()));
                user.setProfilePictureUrl(actualUser.getImage());
                user.setRank(actualUser.getRank());
                user.setPlaceHolder(false);
            }
            drawTitle(challengeNotificationBean);
        } catch (SyncHelper.CouldNotFetchException e) {
            log.error(e.getMessage(), e);
        }
    }

    @UiThread
    void drawTitle(ChallengeNotificationBean notif) {
        if (notificationId != notif.getId()) return; // view has been recycled
        UserBean user = notif.getOpponent();

        if (user.getName() != null) opponentName.setText(user.getName());

        if (!(notif instanceof AutomatchBean)) {
            Picasso.with(context)
                    .load(user.getProfilePictureUrl())
                    .placeholder(R.drawable.default_profile_pic)
                    .transform(new PictureUtils.CropCircle())
                    .into(opponentProfilePic);

            drawRankIcon(user);
        }
    }

    private void drawRankIcon(UserBean user) {
        if (user.getRank() != null) {
            rankIcon.setImageDrawable(getResources().getDrawable(user.getRankDrawable()));
            rankIcon.setVisibility(View.VISIBLE);
        } else {
            rankIcon.setVisibility(View.GONE);
        }
    }
}
