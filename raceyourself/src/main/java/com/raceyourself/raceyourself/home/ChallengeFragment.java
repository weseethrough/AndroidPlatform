package com.raceyourself.raceyourself.home;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.raceyourself.raceyourself.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * A fragment representing a list of Items.
 * <p />
 * <p />
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
@Slf4j
public class ChallengeFragment extends ListFragment implements AbsListView.OnItemClickListener {

    private OnFragmentInteractionListener listener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ChallengeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Change Adapter to display your content
        setListAdapter(new ChallengeListAdapter(getActivity(),
                android.R.layout.simple_list_item_1, new ArrayList<ChallengeNotificationBean>(DummyChallenges.ITEMS)));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (OnFragmentInteractionListener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (listener != null) {
            listener.onFragmentInteraction(DummyChallenges.ITEM_MAP.get(position));
        }
    }

    /**
    * This interface must be implemented by activities that contain this
    * fragment to allow an interaction in this fragment to be communicated
    * to the activity and potentially other fragments contained in that
    * activity.
    * <p>
    * See the Android Training lesson <a href=
    * "http://developer.android.com/training/basics/fragments/communicating.html"
    * >Communicating with Other Fragments</a> for more information.
    */
    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(ChallengeNotificationBean challengeNotification);
    }

    public class ChallengeListAdapter extends ArrayAdapter<ChallengeNotificationBean> {

        //private final String DISTANCE_LABEL = NonSI.MILE.toString();
        //private final UnitConverter metresToMiles = SI.METER.getConverterTo(NonSI.MILE);
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

        private Context context;

        public ChallengeListAdapter(Context context, int textViewResourceId, List<ChallengeNotificationBean> items) {
            super(context, textViewResourceId, items);
            this.context = context;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.fragment_challenge_notification, null);
            }

            ChallengeNotificationBean notif = DummyChallenges.ITEM_MAP.get(position);
            DurationChallengeBean chal = (DurationChallengeBean) notif.getChallenge(); // TODO avoid cast - more generic methods in ChallengeBean? 'limit' and 'goal'?

            TextView itemView = (TextView) view.findViewById(R.id.challenge_notification_challenger_name);
            itemView.setText(notif.getUser().getName());

//            TextView distanceView = (TextView) view.findViewById(R.id.challenge_notification_distance);
//            String distanceText = getString(R.string.challenge_notification_distance);
//            double miles = metresToMiles.convert(chal.getDistanceMetres());
//            distanceView.setText(String.format(distanceText, chal.getDistanceMetres(), DISTANCE_LABEL));

            TextView durationView = (TextView) view.findViewById(R.id.challenge_notification_duration);
            String durationText = getString(R.string.challenge_notification_duration);
            int duration = chal.getDuration().get(GregorianCalendar.MINUTE); // TODO make work for 1+ hours // String.format(dateFormat.format(chal.getDuration().getTime()));
            log.info("Duration text and value: {} / {}", durationText, duration);
            durationView.setText(String.format(durationText, duration));

            TextView expiryView = (TextView) view.findViewById(R.id.challenge_notification_expiry);
            String expiryText = getString(R.string.challenge_expiry);
            durationView.setText(String.format(dateFormat.format(notif.getExpiry().getTime())));

            return view;
        }
    }
}
