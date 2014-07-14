package com.raceyourself.platform.models;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raceyourself.platform.gpstracker.SyncHelper;
import com.roscopeco.ormdroid.Entity;
import com.roscopeco.ormdroid.ORMDroidApplication;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static com.roscopeco.ormdroid.Query.and;
import static com.roscopeco.ormdroid.Query.eql;

/**
 * A challenge.
 * 
 * Consistency model: Client can implicitly create using Actions.
 *                    Server can upsert using id.
 */
public class Challenge extends EntityCollection.CollectionEntity {

    @JsonIgnore
    public long id;
    public int device_id;
    public int challenge_id;
    public Date start_time;
    public Date stop_time;
    @JsonProperty("public")
    public boolean isPublic;
    public int creator_id;
    public int distance;
    public int duration;
    public String name;
    public String description;
    public int points_awarded;
    public String prize;
    public String type;

    private List<ChallengeAttempt> transientAttempts = new LinkedList<ChallengeAttempt>();
    private List<ChallengeFriend> transientFriends = new LinkedList<ChallengeFriend>();

    public Date deleted_at;

    @JsonIgnore
    public boolean dirty = false;

    public Challenge() {
        Device device = Device.self();
        if (device == null) this.device_id = 0;
        else this.device_id = device.getId();
        this.challenge_id = Sequence.getNext("challenge_id");
        this.dirty = true;
    }

    public static Challenge get(int deviceId, int challengeId) {
        return query(Challenge.class).where(and(eql("device_id", deviceId), eql("challenge_id", challengeId))).execute();
    }
    
    public static List<Challenge> getPersonalChallenges() {
        EntityCollection defaultCollection = EntityCollection.getDefault();        
        return defaultCollection.getItems(Challenge.class);
    }

    public void setAttempts(List<ChallengeAttempt> attempts) {
        for (ChallengeAttempt attempt : attempts) {
            attempt.challenge_device_id = this.device_id;
            attempt.challenge_id = this.challenge_id;
            transientAttempts.add(attempt);
        }
    }

    public List<Track> getTracks() {
        List<Track> trackList = new ArrayList<Track>();
        for(ChallengeAttempt attempt : getAttempts()) {
            trackList.add(SyncHelper.getTrack(attempt.track_device_id, attempt.track_id));
        }
        return trackList;
    }

    public Track getTrackByUser(int userId) {
        for(ChallengeAttempt attempt : getAttempts()) {
            if(attempt.user_id == userId) {
                return SyncHelper.getTrack(attempt.track_device_id, attempt.track_id);
            }
        }
        return null;
    }

    public void addAttempt(Track track) {
        if(mTransient) {
            transientAttempts.add(new ChallengeAttempt(this, track));
            return;
        }
        try {
            Action.ChallengeAttemptAction aa = new Action.ChallengeAttemptAction(this, track);
            Action action = new Action(new ObjectMapper().writeValueAsString(aa));
            action.save();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Should never happen", e);
        }
        // Store attempt client-side (will be replaced by server on sync)
        ChallengeAttempt attempt = new ChallengeAttempt(this, track);
        attempt.save();
    }

    public void accept() {
        try {
            Action.AcceptChallengeAction aca = new Action.AcceptChallengeAction(this);
            Action action = new Action(new ObjectMapper().writeValueAsString(aca));
            action.save();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Should never happen", e);
        }
    }

    public void challengeUser(User user) {
        challengeUser(user.getId());
    }

    public void challengeUser(int userId) {
        queueChallenge(String.valueOf(userId));
    }

    public void challengeFriend(Friend friend) {
        if (friend.user_id != null) challengeUser(friend.user_id);
        else queueChallenge(friend.uid);
    }

    private void queueChallenge(String target) {
        try {
            Action.ChallengeAction ca = new Action.ChallengeAction(this, target);
            Action action = new Action(new ObjectMapper().writeValueAsString(ca));
            action.save();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Should never happen", e);
        }
    }

    public List<ChallengeAttempt> getAttempts() {
        if(mTransient) {
            return transientAttempts;
        }
        return query(ChallengeAttempt.class).where(and(eql("challenge_device_id", this.device_id), eql("challenge_id", this.challenge_id))).executeMulti();
    }

    public void clearAttempts() {
        List<ChallengeAttempt> attempts = getAttempts();
        for (ChallengeAttempt attempt : attempts) {
            attempt.delete();
        }
    }

    public void setFriends(List<Integer> friends) {
        for (Integer friendId : friends) {
            ChallengeFriend friend = new ChallengeFriend(this, friendId);
            transientFriends.add(friend);
        }
    }

    public List<ChallengeFriend> getFriends() {
        if(mTransient) {
            return transientFriends;
        }
        return query(ChallengeFriend.class).where(and(eql("device_id", this.device_id), eql("challenge_id", this.challenge_id))).executeMulti();
    }

    public void clearFriends() {
        List<ChallengeFriend> friends = getFriends();
        for (ChallengeFriend friend : friends) {
            friend.delete();
        }
    }

    @Override
    public int store() {
        int ret = -1;
        if (id == 0) {
            ByteBuffer encodedId = ByteBuffer.allocate(8);
            encodedId.putInt(device_id);
            encodedId.putInt(challenge_id);
            encodedId.flip();
            this.id = encodedId.getLong();
        }
        if (mTransient) {
            ORMDroidApplication.getInstance().beginTransaction();
            try {
                clearAttempts();
                for (ChallengeAttempt attempt : transientAttempts) {
                    // Foreign key may be null if fields deserialized in wrong order, update.
                    attempt.challenge_device_id = this.device_id;
                    attempt.challenge_id = this.challenge_id;
                    attempt.save();
                }
                clearFriends();
                for (ChallengeFriend friend : transientFriends) {
                    // Foreign key may be null if fields deserialized in wrong order, update.
                    friend.device_id = this.device_id;
                    friend.challenge_id = this.challenge_id;
                    friend.save();
                }
                ret = super.store();
                ORMDroidApplication.getInstance().setTransactionSuccessful();
            } finally {
                transientAttempts.clear();
                transientFriends.clear();
                ORMDroidApplication.getInstance().endTransaction();
            }
        } else {
            ret = super.store();
        }
        return ret;
    }

    @Override
    public void delete() {
        clearAttempts();
        clearFriends();
        super.delete();
    }

    @Override
    public void erase() {
        delete();
    }

    public void flush() {
        if (deleted_at != null) {
            super.delete();
            return;
        }
        if (dirty) {
            dirty = false;
            save();
        }
    }

    public static class ChallengeAttempt extends Entity {
        @JsonIgnore
        public int id; // auto-incremented
        @JsonIgnore
        public int challenge_device_id;
        @JsonIgnore
        public int challenge_id;

        @JsonProperty("device_id")
        public int track_device_id;
        public int track_id;
        public int user_id;

        public ChallengeAttempt() {}

        public ChallengeAttempt(Challenge challenge, Track track) {
            this.challenge_device_id = challenge.device_id;
            this.challenge_id = challenge.challenge_id;
            this.track_device_id = track.device_id;
            this.track_id = track.track_id;
            this.user_id = track.user_id;
        }
    }

    public static class ChallengeFriend extends Entity {
        @JsonIgnore
        public int id; // auto-incremented
        @JsonIgnore
        public int device_id;
        @JsonIgnore
        public int challenge_id;

        public int friend_id;

        public ChallengeFriend() {}

        public ChallengeFriend(Challenge challenge, int friendId) {
            this.device_id = challenge.device_id;
            this.challenge_id = challenge.challenge_id;
            this.friend_id = friendId;
        }
    }
}
