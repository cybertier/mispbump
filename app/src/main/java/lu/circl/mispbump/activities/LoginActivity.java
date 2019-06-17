package lu.circl.mispbump.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.constraintlayout.widget.ConstraintLayout;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;

import java.util.Objects;

import lu.circl.mispbump.R;
import lu.circl.mispbump.auxiliary.DialogManager;
import lu.circl.mispbump.auxiliary.PreferenceManager;
import lu.circl.mispbump.restful_client.MispRestClient;
import lu.circl.mispbump.restful_client.Organisation;
import lu.circl.mispbump.restful_client.User;

/**
 * This activity is shown when the current device has no misp user associated with it.
 * Takes care of downloading all information necessary for a sync with other misp instances.
 */
public class LoginActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private ConstraintLayout constraintLayout;
    private TextInputLayout serverAutomationKey;
    private TextInputLayout serverUrl;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initializeViews();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_login_help) {
            DialogManager.loginHelpDialog(LoginActivity.this);
            return true;
        }

        // invoke superclass to handle unrecognized item (eg. homeAsUp)
        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        // populate Toolbar (Actionbar)
        Toolbar myToolbar = findViewById(R.id.appbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(false);
        }

        constraintLayout = findViewById(R.id.rootLayout);

        serverUrl = findViewById(R.id.login_server_url);
        serverAutomationKey = findViewById(R.id.login_automation_key);
        Button downloadInfoButton = findViewById(R.id.login_download_button);
        downloadInfoButton.setOnClickListener(onClickDownload);

        progressBar = findViewById(R.id.login_progressbar);

        preferenceManager = PreferenceManager.getInstance(this);
    }

    /**
     * Is called when the user clicks on the login button.
     */
    private View.OnClickListener onClickDownload = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            String url = Objects.requireNonNull(serverUrl.getEditText()).getText().toString();
            String authkey = Objects.requireNonNull(serverAutomationKey.getEditText()).getText().toString();

            boolean error = false;

            serverUrl.setError(null);
            serverAutomationKey.setError(null);

            if (!isValidUrl(url)) {
                error = true;
                serverUrl.setError("Invalid Server URL");
            }

            if (!isValidAutomationKey(authkey)) {
                error = true;
                serverAutomationKey.setError("Invalid automation key");
            }

            if (error) {
                return;
            }

            // save authkey
            preferenceManager.setAutomationKey(authkey);

            // save url
            preferenceManager.setServerUrl(url);

            // instance of MispRestClient with given URL
            final MispRestClient mispRestClient = new MispRestClient(getApplicationContext());

            // display progress bar
            progressBar.setVisibility(View.VISIBLE);

            // get my user information and the organisation associated with my user
            mispRestClient.isAvailable(new MispRestClient.AvailableCallback() {
                @Override
                public void available() {
                    mispRestClient.getMyUser(new MispRestClient.UserCallback() {
                        @Override
                        public void success(final User user) {
                            preferenceManager.setUserInfo(user);
                            mispRestClient.getOrganisation(user.org_id, new MispRestClient.OrganisationCallback() {
                                @Override
                                public void success(Organisation organisation) {
                                    preferenceManager.setUserOrgInfo(organisation);
                                    progressBar.setVisibility(View.GONE);
                                    Intent home = new Intent(getApplicationContext(), HomeActivity.class);
                                    startActivity(home);
                                    finish();
                                }

                                @Override
                                public void failure(String error) {
                                    progressBar.setVisibility(View.GONE);
                                    Snackbar.make(constraintLayout, error, Snackbar.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void failure(String error) {
                            progressBar.setVisibility(View.GONE);
                            Snackbar.make(constraintLayout, error, Snackbar.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void unavailable(String error) {
                    progressBar.setVisibility(View.GONE);
                    Snackbar sb = Snackbar.make(constraintLayout, error, Snackbar.LENGTH_LONG);
                    sb.show();
                }
            });
        }
    };

    /**
     * Check if url is valid.
     *
     * @param url url to check
     * @return true or false
     */
    private boolean isValidUrl(String url) {
        Uri uri = Uri.parse(url);

        if (uri == null) {
            return false;
        }

        return uri.getScheme() != null;
    }

    /**
     * Check if automation key is valid.
     *
     * @param automationKey the key to check
     * @return true or false
     */
    private boolean isValidAutomationKey(String automationKey) {
        return !TextUtils.isEmpty(automationKey);
    }
}