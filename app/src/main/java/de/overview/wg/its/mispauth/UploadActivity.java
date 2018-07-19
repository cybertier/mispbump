package de.overview.wg.its.mispauth;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import com.android.volley.VolleyError;
import com.google.gson.Gson;
import de.overview.wg.its.mispauth.adapter.UploadStateAdapter;
import de.overview.wg.its.mispauth.auxiliary.PreferenceManager;
import de.overview.wg.its.mispauth.auxiliary.ReadableError;
import de.overview.wg.its.mispauth.auxiliary.TempAuth;
import de.overview.wg.its.mispauth.model.*;
import de.overview.wg.its.mispauth.network.MispRequest;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ConstantConditions")
public class UploadActivity extends AppCompatActivity implements View.OnClickListener {

	static final String PARTNER_INFO_BUNDLE_KEY = "partner_info";

	private MispRequest mispRequest;
	private SyncInformationQr partnerInformation;

	private Organisation partnerOrganisation;
	private User partnerSyncUser;
	private Server partnerServer;

	private UploadStateAdapter uploadStateAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_upload);

		Bundle b = getIntent().getExtras();
		assert b != null;
		String info = b.getString(PARTNER_INFO_BUNDLE_KEY);

		partnerInformation = new Gson().fromJson(info, SyncInformationQr.class);

		mispRequest = MispRequest.Instance(this, true);

		initializeViews();

		SyncUpload();
	}

	private void initializeViews() {

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		getSupportActionBar().setDisplayShowHomeEnabled(false);
		getSupportActionBar().setDisplayHomeAsUpEnabled(false);
		getSupportActionBar().setDisplayShowTitleEnabled(true);

		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(this);

		RecyclerView recyclerView = findViewById(R.id.recyclerView);
		uploadStateAdapter = new UploadStateAdapter();
		RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
		recyclerView.setLayoutManager(mLayoutManager);
		recyclerView.setItemAnimator(new DefaultItemAnimator());
		recyclerView.setAdapter(uploadStateAdapter);
	}

	@Override
	public void onClick(View v) {

		int id = v.getId();

		switch (id) {
			case R.id.fab:
				finish();
				break;
		}
	}

	private void setCurrentStateWrapper(int stateNumber, UploadState.State state) {
		syncUploadStates.get(stateNumber).setCurrentState(state);
		uploadStateAdapter.notifyItemChanged(stateNumber);
	}

	private List<UploadState> syncUploadStates = new ArrayList<>();

	private void SyncUpload() {

		partnerOrganisation = partnerInformation.getOrganisation();
		partnerSyncUser = partnerInformation.getUser();
		partnerServer = partnerInformation.getServer();

		syncUploadStates.add(new UploadState("Add local organisation"));
		syncUploadStates.add(new UploadState("Add sync user to organisation"));
		syncUploadStates.add(new UploadState("Add external organisation"));
		syncUploadStates.add(new UploadState("Add sync server"));

		uploadStateAdapter.setStateList(syncUploadStates);

		uploadSyncOrganisation();
	}

	private void uploadSyncOrganisation() {
		setCurrentStateWrapper(0, UploadState.State.IN_PROGRESS);
//		syncUploadStates.get(0).setCurrentState(UploadState.State.IN_PROGRESS);
		mispRequest.addOrganisation(partnerOrganisation, new MispRequest.OrganisationCallback() {
			@Override
			public void onResult(JSONObject organisationInformation) {
				try {

					Organisation retOrg = new Organisation(organisationInformation);
					setCurrentStateWrapper(0, UploadState.State.DONE);
//					syncUploadStates.get(0).setCurrentState(UploadState.State.DONE);
					uploadSyncUser(retOrg.getId());

				} catch (JSONException e) {
					syncUploadStates.get(0).setError("Could not read server response");
					setCurrentStateWrapper(0, UploadState.State.ERROR);
//					syncUploadStates.get(0).setCurrentState(UploadState.State.ERROR);
					e.printStackTrace();
				}

			}

			@Override
			public void onError(VolleyError volleyError) {
				syncUploadStates.get(0).setError(ReadableError.toReadable(volleyError));
				setCurrentStateWrapper(0, UploadState.State.ERROR);
//				syncUploadStates.get(0).setCurrentState(UploadState.State.ERROR);
			}
		});
	}

	private void uploadSyncUser(int orgID) {

		setCurrentStateWrapper(1, UploadState.State.IN_PROGRESS);
//		syncUploadStates.get(1).setCurrentState(UploadState.State.IN_PROGRESS);

		partnerSyncUser.setOrgId(orgID);
		partnerSyncUser.setAuthkey(TempAuth.TMP_AUTH_KEY);
		partnerSyncUser.setRoleId(User.RoleId.SYNC_USER);

		mispRequest.addUser(partnerSyncUser, new MispRequest.UserCallback() {
			@Override
			public void onResult(JSONObject myUserInformation) {
				setCurrentStateWrapper(1, UploadState.State.DONE);
//				syncUploadStates.get(1).setCurrentState(UploadState.State.DONE);
				uploadExternalSyncOrganisation();
			}

			@Override
			public void onError(VolleyError volleyError) {
				syncUploadStates.get(1).setError(ReadableError.toReadable(volleyError));
				setCurrentStateWrapper(1, UploadState.State.ERROR);
//				syncUploadStates.get(1).setCurrentState(UploadState.State.ERROR);
			}
		});
	}

	private void uploadExternalSyncOrganisation() {

		setCurrentStateWrapper(2, UploadState.State.IN_PROGRESS);

		partnerOrganisation.setName(partnerOrganisation.getName() + " (Remote)");
		partnerOrganisation.setLocal(false);

		mispRequest.addOrganisation(partnerOrganisation, new MispRequest.OrganisationCallback() {
			@Override
			public void onResult(JSONObject organisationInformation) {
				try {

					Organisation extOrg = new Organisation(organisationInformation);
					setCurrentStateWrapper(2, UploadState.State.DONE);
					uploadSyncServer(extOrg.getId());

				} catch (JSONException e) {
					syncUploadStates.get(2).setError("Could not read server response");
					setCurrentStateWrapper(2, UploadState.State.ERROR);
					e.printStackTrace();
				}
			}

			@Override
			public void onError(VolleyError volleyError) {
				syncUploadStates.get(2).setError(ReadableError.toReadable(volleyError));
				setCurrentStateWrapper(2, UploadState.State.ERROR);
			}
		});
	}

	private void uploadSyncServer(int remoteOrgId) {

		setCurrentStateWrapper(3, UploadState.State.IN_PROGRESS);

		partnerServer.setRemoteOrgId(remoteOrgId);
		partnerServer.setPush(true);

		mispRequest.addServer(partnerServer, new MispRequest.ServerCallback() {
			@Override
			public void onResult(JSONObject servers) {
				setCurrentStateWrapper(3, UploadState.State.DONE);
				updateSyncedOrganisationList();
			}

			@Override
			public void onError(VolleyError volleyError) {
				syncUploadStates.get(3).setError(ReadableError.toReadable(volleyError));
				setCurrentStateWrapper(3, UploadState.State.ERROR);
			}
		});
	}

	private void updateSyncedOrganisationList() {

		PreferenceManager preferenceManager = PreferenceManager.Instance(this);

		List<SyncedPartner> syncedPartnerList = preferenceManager.getSyncedPartnerList();

		if (syncedPartnerList == null) {
			syncedPartnerList = new ArrayList<>();
		}

		SyncedPartner sp = new SyncedPartner(
				partnerInformation.getOrganisation().getName(),
				partnerInformation.getServer().getUrl());

		sp.generateTimeStamp();
		syncedPartnerList.add(sp);
		preferenceManager.setSyncedPartnerList(syncedPartnerList);
	}
}
