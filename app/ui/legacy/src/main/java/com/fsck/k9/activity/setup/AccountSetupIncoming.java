
package com.fsck.k9.activity.setup;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.fsck.k9.Account;
import com.fsck.k9.DI;
import com.fsck.k9.LocalKeyStoreManager;
import com.fsck.k9.Preferences;
import com.fsck.k9.account.AccountCreator;
import com.fsck.k9.helper.EmailHelper;
import com.fsck.k9.setup.ServerNameSuggester;
import com.fsck.k9.ui.base.K9Activity;
import com.fsck.k9.activity.setup.AccountSetupCheckSettings.CheckDirection;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.AuthType;
import com.fsck.k9.mail.ConnectionSecurity;
import com.fsck.k9.mail.MailServerDirection;
import com.fsck.k9.mail.ServerSettings;
import com.fsck.k9.mail.store.imap.ImapStoreSettings;
import com.fsck.k9.mail.store.webdav.WebDavStoreSettings;
import com.fsck.k9.preferences.Protocols;
import com.fsck.k9.ui.R;
import com.fsck.k9.ui.base.extensions.TextInputLayoutHelper;
import com.fsck.k9.view.ClientCertificateSpinner;
import com.fsck.k9.view.ClientCertificateSpinner.OnClientCertificateChangedListener;

import java.util.Locale;
import java.util.Map;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import timber.log.Timber;

import static java.util.Collections.emptyMap;


public class AccountSetupIncoming extends K9Activity implements OnClickListener {
    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_MAKE_DEFAULT = "makeDefault";
    private static final String STATE_SECURITY_TYPE_POSITION = "stateSecurityTypePosition";
    private static final String STATE_AUTH_TYPE_POSITION = "authTypePosition";

    private final AccountCreator accountCreator = DI.get(AccountCreator.class);
    private final ServerNameSuggester serverNameSuggester = DI.get(ServerNameSuggester.class);

    private String mStoreType;
    private TextInputEditText mUsernameView;
    private TextInputEditText mPasswordView;
    private ClientCertificateSpinner mClientCertificateSpinner;
    private TextInputLayout mPasswordLayoutView;
    private TextInputEditText mServerView;
    private TextInputEditText mPortView;
    private String mCurrentPortViewSetting;
    private Spinner mSecurityTypeView;
    private int mCurrentSecurityTypeViewPosition;
    private Spinner mAuthTypeView;
    private int mCurrentAuthTypeViewPosition;
    private CheckBox mImapAutoDetectNamespaceView;
    private TextInputEditText mImapPathPrefixView;
    private TextInputEditText mWebdavPathPrefixView;
    private TextInputEditText mWebdavAuthPathView;
    private TextInputEditText mWebdavMailboxPathView;
    private ViewGroup mAllowClientCertificateView;
    private Button mNextButton;
    private Account mAccount;
    private boolean mMakeDefault;
    private CheckBox useCompressionCheckBox;
    private CheckBox mSubscribedFoldersOnly;
    private AuthTypeAdapter mAuthTypeAdapter;
    private ConnectionSecurity[] mConnectionSecurityChoices = ConnectionSecurity.values();

    public static void actionIncomingSettings(Activity context, Account account, boolean makeDefault) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        i.putExtra(EXTRA_MAKE_DEFAULT, makeDefault);
        context.startActivity(i);
    }

    public static void actionEditIncomingSettings(Context context, String accountUuid) {
        Intent intent = new Intent(context, AccountSetupIncoming.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(EXTRA_ACCOUNT, accountUuid);

        context.startActivity(intent);
    }

    public static Intent intentActionEditIncomingSettings(Context context, Account account) {
        Intent i = new Intent(context, AccountSetupIncoming.class);
        i.setAction(Intent.ACTION_EDIT);
        i.putExtra(EXTRA_ACCOUNT, account.getUuid());
        return i;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLayout(R.layout.account_setup_incoming);
        setTitle(R.string.account_setup_incoming_title);

        mUsernameView = findViewById(R.id.account_username);
        mPasswordView = findViewById(R.id.account_password);
        mClientCertificateSpinner = findViewById(R.id.account_client_certificate_spinner);
        mPasswordLayoutView = findViewById(R.id.account_password_layout);
        mServerView = findViewById(R.id.account_server);
        mPortView = findViewById(R.id.account_port);
        mSecurityTypeView = findViewById(R.id.account_security_type);
        mAuthTypeView = findViewById(R.id.account_auth_type);
        mImapAutoDetectNamespaceView = findViewById(R.id.imap_autodetect_namespace);
        mImapPathPrefixView = findViewById(R.id.imap_path_prefix);
        mWebdavPathPrefixView = findViewById(R.id.webdav_path_prefix);
        mWebdavAuthPathView = findViewById(R.id.webdav_auth_path);
        mWebdavMailboxPathView = findViewById(R.id.webdav_mailbox_path);
        mNextButton = findViewById(R.id.next);
        useCompressionCheckBox = findViewById(R.id.use_compression);
        mSubscribedFoldersOnly = findViewById(R.id.subscribed_folders_only);
        mAllowClientCertificateView = findViewById(R.id.account_allow_client_certificate);

        TextInputLayout serverLayoutView = findViewById(R.id.account_server_layout);

        mNextButton.setOnClickListener(this);

        mImapAutoDetectNamespaceView.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mImapPathPrefixView.setEnabled(!isChecked);
                if (isChecked && mImapPathPrefixView.hasFocus()) {
                    mImapPathPrefixView.focusSearch(View.FOCUS_UP).requestFocus();
                } else if (!isChecked) {
                    mImapPathPrefixView.requestFocus();
                }
            }
        });

        mAuthTypeAdapter = AuthTypeAdapter.get(this);
        mAuthTypeView.setAdapter(mAuthTypeAdapter);

        /*
         * Only allow digits in the port field.
         */
        mPortView.setKeyListener(DigitsKeyListener.getInstance("0123456789"));

        String accountUuid = getIntent().getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        mMakeDefault = getIntent().getBooleanExtra(EXTRA_MAKE_DEFAULT, false);

        /*
         * If we're being reloaded we override the original account with the one
         * we saved
         */
        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_ACCOUNT)) {
            accountUuid = savedInstanceState.getString(EXTRA_ACCOUNT);
            mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        }

        boolean editSettings = Intent.ACTION_EDIT.equals(getIntent().getAction());
        if (editSettings) {
            TextInputLayoutHelper.configureAuthenticatedPasswordToggle(
                    mPasswordLayoutView,
                    this,
                    getString(R.string.account_setup_basics_show_password_biometrics_title),
                    getString(R.string.account_setup_basics_show_password_biometrics_subtitle),
                    getString(R.string.account_setup_basics_show_password_need_lock)
            );
        }

        try {
            ServerSettings settings = mAccount.getIncomingServerSettings();

            if (savedInstanceState == null) {
                // The first item is selected if settings.authenticationType is null or is not in mAuthTypeAdapter
                mCurrentAuthTypeViewPosition = mAuthTypeAdapter.getAuthPosition(settings.authenticationType);
            } else {
                mCurrentAuthTypeViewPosition = savedInstanceState.getInt(STATE_AUTH_TYPE_POSITION);
            }
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            updateViewFromAuthType();

            mUsernameView.setText(settings.username);

            if (settings.password != null) {
                mPasswordView.setText(settings.password);
            }

            if (settings.clientCertificateAlias != null) {
                mClientCertificateSpinner.setAlias(settings.clientCertificateAlias);
            }

            mStoreType = settings.type;
            if (settings.type.equals(Protocols.POP3)) {
                serverLayoutView.setHint(getString(R.string.account_setup_incoming_pop_server_label));
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);
                useCompressionCheckBox.setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);
            } else if (settings.type.equals(Protocols.IMAP)) {
                serverLayoutView.setHint(getString(R.string.account_setup_incoming_imap_server_label));

                boolean autoDetectNamespace = ImapStoreSettings.getAutoDetectNamespace(settings);
                String pathPrefix = ImapStoreSettings.getPathPrefix(settings);

                mImapAutoDetectNamespaceView.setChecked(autoDetectNamespace);
                if (pathPrefix != null) {
                    mImapPathPrefixView.setText(pathPrefix);
                }

                findViewById(R.id.webdav_advanced_header).setVisibility(View.GONE);
                findViewById(R.id.webdav_mailbox_alias_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_owa_path_section).setVisibility(View.GONE);
                findViewById(R.id.webdav_auth_path_section).setVisibility(View.GONE);

                if (!editSettings) {
                    findViewById(R.id.imap_folder_setup_section).setVisibility(View.GONE);
                }
            } else if (settings.type.equals(Protocols.WEBDAV)) {
                serverLayoutView.setHint(getString(R.string.account_setup_incoming_webdav_server_label));
                mConnectionSecurityChoices = new ConnectionSecurity[] {
                        ConnectionSecurity.NONE,
                        ConnectionSecurity.SSL_TLS_REQUIRED };

                // Hide the unnecessary fields
                findViewById(R.id.imap_path_prefix_section).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type_label).setVisibility(View.GONE);
                findViewById(R.id.account_auth_type).setVisibility(View.GONE);
                useCompressionCheckBox.setVisibility(View.GONE);
                mSubscribedFoldersOnly.setVisibility(View.GONE);

                String path = WebDavStoreSettings.getPath(settings);
                if (path != null) {
                    mWebdavPathPrefixView.setText(path);
                }

                String authPath = WebDavStoreSettings.getAuthPath(settings);
                if (authPath != null) {
                    mWebdavAuthPathView.setText(authPath);
                }

                String mailboxPath = WebDavStoreSettings.getMailboxPath(settings);
                if (mailboxPath != null) {
                    mWebdavMailboxPathView.setText(mailboxPath);
                }
            } else {
                throw new Exception("Unknown account type: " + settings.type);
            }

            if (!editSettings) {
                mAccount.setDeletePolicy(accountCreator.getDefaultDeletePolicy(settings.type));
            }

            // Note that mConnectionSecurityChoices is configured above based on server type
            ConnectionSecurityAdapter securityTypesAdapter =
                    ConnectionSecurityAdapter.get(this, mConnectionSecurityChoices);
            mSecurityTypeView.setAdapter(securityTypesAdapter);

            // Select currently configured security type
            if (savedInstanceState == null) {
                mCurrentSecurityTypeViewPosition = securityTypesAdapter.getConnectionSecurityPosition(settings.connectionSecurity);
            } else {

                /*
                 * Restore the spinner state now, before calling
                 * setOnItemSelectedListener(), thus avoiding a call to
                 * onItemSelected(). Then, when the system restores the state
                 * (again) in onRestoreInstanceState(), The system will see that
                 * the new state is the same as the current state (set here), so
                 * once again onItemSelected() will not be called.
                 */
                mCurrentSecurityTypeViewPosition = savedInstanceState.getInt(STATE_SECURITY_TYPE_POSITION);
            }
            mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);

            updateAuthPlainTextFromSecurityType(settings.connectionSecurity);
            updateViewFromSecurity();

            useCompressionCheckBox.setChecked(mAccount.useCompression());

            if (settings.host != null) {
                mServerView.setText(settings.host);
            }

            if (settings.port != -1) {
                mPortView.setText(String.format(Locale.ROOT, "%d", settings.port));
            } else {
                updatePortFromSecurityType();
            }
            mCurrentPortViewSetting = mPortView.getText().toString();

            mSubscribedFoldersOnly.setChecked(mAccount.isSubscribedFoldersOnly());
        } catch (Exception e) {
            failure(e);
        }
    }

    /**
     * Called at the end of either {@code onCreate()} or
     * {@code onRestoreInstanceState()}, after the views have been initialized,
     * so that the listeners are not triggered during the view initialization.
     * This avoids needless calls to {@code validateFields()} which is called
     * immediately after this is called.
     */
    private void initializeViewListeners() {

        /*
         * Updates the port when the user changes the security type. This allows
         * us to show a reasonable default which the user can change.
         */
        mSecurityTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {

                /*
                 * We keep our own record of the spinner state so we
                 * know for sure that onItemSelected() was called
                 * because of user input, not because of spinner
                 * state initialization. This assures that the port
                 * will not be replaced with a default value except
                 * on user input.
                 */
                if (mCurrentSecurityTypeViewPosition != position) {
                    updatePortFromSecurityType();
                    validateFields();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mAuthTypeView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                    long id) {
                if (mCurrentAuthTypeViewPosition == position) {
                    return;
                }

                updateViewFromAuthType();
                updateViewFromSecurity();
                validateFields();
                AuthType selection = getSelectedAuthType();

               // Have the user select the client certificate if not already selected
               if ((AuthType.EXTERNAL == selection) && (mClientCertificateSpinner.getAlias() == null)) {
                    // This may again invoke validateFields()
                    mClientCertificateSpinner.chooseCertificate();
                } else {
                    mPasswordView.requestFocus();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* unused */ }
        });

        mClientCertificateSpinner.setOnClientCertificateChangedListener(clientCertificateChangedListener);
        mUsernameView.addTextChangedListener(validationTextWatcher);
        mPasswordView.addTextChangedListener(validationTextWatcher);
        mServerView.addTextChangedListener(validationTextWatcher);
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_ACCOUNT, mAccount.getUuid());
        outState.putInt(STATE_SECURITY_TYPE_POSITION, mCurrentSecurityTypeViewPosition);
        outState.putInt(STATE_AUTH_TYPE_POSITION, mCurrentAuthTypeViewPosition);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        /*
         * We didn't want the listeners active while the state was being restored
         * because they could overwrite the restored port with a default port when
         * the security type was restored.
         */
        initializeViewListeners();
        validateFields();
    }

    /**
     * Shows/hides password field and client certificate spinner
     */
    private void updateViewFromAuthType() {
        AuthType authType = getSelectedAuthType();
        boolean isAuthTypeExternal = (AuthType.EXTERNAL == authType);

        if (isAuthTypeExternal) {

            // hide password fields, show client certificate fields
            mPasswordLayoutView.setVisibility(View.GONE);
        } else {

            // show password fields, hide client certificate fields
            mPasswordLayoutView.setVisibility(View.VISIBLE);
        }
    }


    /**
     * Shows/hides client certificate spinner
     */
    private void updateViewFromSecurity() {
        ConnectionSecurity security = getSelectedSecurity();
        boolean isUsingTLS = ((ConnectionSecurity.SSL_TLS_REQUIRED  == security) || (ConnectionSecurity.STARTTLS_REQUIRED == security));

        if (isUsingTLS) {
            mAllowClientCertificateView.setVisibility(View.VISIBLE);
        } else {
            mAllowClientCertificateView.setVisibility(View.GONE);
        }
    }

    /**
     * This is invoked only when the user makes changes to a widget, not when
     * widgets are changed programmatically.  (The logic is simpler when you know
     * that this is the last thing called after an input change.)
     */
    private void validateFields() {
        AuthType authType = getSelectedAuthType();
        boolean isAuthTypeExternal = (AuthType.EXTERNAL == authType);

        ConnectionSecurity connectionSecurity = getSelectedSecurity();
        boolean hasConnectionSecurity = (connectionSecurity != ConnectionSecurity.NONE);

        if (isAuthTypeExternal && !hasConnectionSecurity) {

            // Notify user of an invalid combination of AuthType.EXTERNAL & ConnectionSecurity.NONE
            String toastText = getString(R.string.account_setup_incoming_invalid_setting_combo_notice,
                    getString(R.string.account_setup_incoming_auth_type_label),
                    AuthType.EXTERNAL.toString(),
                    getString(R.string.account_setup_incoming_security_label),
                    ConnectionSecurity.NONE.toString());
            Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();

            // Reset the views back to their previous settings without recursing through here again
            OnItemSelectedListener onItemSelectedListener = mAuthTypeView.getOnItemSelectedListener();
            mAuthTypeView.setOnItemSelectedListener(null);
            mAuthTypeView.setSelection(mCurrentAuthTypeViewPosition, false);
            mAuthTypeView.setOnItemSelectedListener(onItemSelectedListener);
            updateViewFromAuthType();
            updateViewFromSecurity();

            onItemSelectedListener = mSecurityTypeView.getOnItemSelectedListener();
            mSecurityTypeView.setOnItemSelectedListener(null);
            mSecurityTypeView.setSelection(mCurrentSecurityTypeViewPosition, false);
            mSecurityTypeView.setOnItemSelectedListener(onItemSelectedListener);
            updateAuthPlainTextFromSecurityType(getSelectedSecurity());
            updateViewFromSecurity();

            mPortView.removeTextChangedListener(validationTextWatcher);
            mPortView.setText(mCurrentPortViewSetting);
            mPortView.addTextChangedListener(validationTextWatcher);

            authType = getSelectedAuthType();
            isAuthTypeExternal = (AuthType.EXTERNAL == authType);

            connectionSecurity = getSelectedSecurity();
            hasConnectionSecurity = (connectionSecurity != ConnectionSecurity.NONE);
        } else {
            mCurrentAuthTypeViewPosition = mAuthTypeView.getSelectedItemPosition();
            mCurrentSecurityTypeViewPosition = mSecurityTypeView.getSelectedItemPosition();
            mCurrentPortViewSetting = mPortView.getText().toString();
        }

        boolean hasValidCertificateAlias = mClientCertificateSpinner.getAlias() != null;
        boolean hasValidUserName = Utility.requiredFieldValid(mUsernameView);

        boolean hasValidPasswordSettings = hasValidUserName
                && !isAuthTypeExternal
                && Utility.requiredFieldValid(mPasswordView);

        boolean hasValidExternalAuthSettings = hasValidUserName
                && isAuthTypeExternal
                && hasConnectionSecurity
                && hasValidCertificateAlias;

        mNextButton.setEnabled(Utility.domainFieldValid(mServerView)
                && Utility.requiredFieldValid(mPortView)
                && (hasValidPasswordSettings || hasValidExternalAuthSettings));
        Utility.setCompoundDrawablesAlpha(mNextButton, mNextButton.isEnabled() ? 255 : 128);
    }

    private void updatePortFromSecurityType() {
        ConnectionSecurity securityType = getSelectedSecurity();
        updateAuthPlainTextFromSecurityType(securityType);
        updateViewFromSecurity();

        // Remove listener so as not to trigger validateFields() which is called
        // elsewhere as a result of user interaction.
        mPortView.removeTextChangedListener(validationTextWatcher);
        mPortView.setText(String.valueOf(accountCreator.getDefaultPort(securityType, mStoreType)));
        mPortView.addTextChangedListener(validationTextWatcher);
    }

    private void updateAuthPlainTextFromSecurityType(ConnectionSecurity securityType) {
        mAuthTypeAdapter.useInsecureText(securityType == ConnectionSecurity.NONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != AccountSetupCheckSettings.ACTIVITY_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (resultCode == RESULT_OK) {
            if (Intent.ACTION_EDIT.equals(getIntent().getAction())) {
                Preferences.getPreferences(getApplicationContext()).saveAccount(mAccount);
                finish();
            } else {
                /*
                 * Set the username and password for the outgoing settings to the username and
                 * password the user just set for incoming.
                 */
                String username = mUsernameView.getText().toString().trim();

                String password = null;
                String clientCertificateAlias = null;
                AuthType authType = getSelectedAuthType();
                if ((ConnectionSecurity.SSL_TLS_REQUIRED == getSelectedSecurity()) ||
                        (ConnectionSecurity.STARTTLS_REQUIRED == getSelectedSecurity()) ) {
                    clientCertificateAlias = mClientCertificateSpinner.getAlias();
                }
                if (AuthType.EXTERNAL != authType) {
                    password = mPasswordView.getText().toString();
                }

                String domain = EmailHelper.getDomainFromEmailAddress(mAccount.getEmail());
                String host = serverNameSuggester.suggestServerName(Protocols.SMTP, domain);
                ServerSettings transportServer = new ServerSettings(Protocols.SMTP, host,
                        -1, ConnectionSecurity.SSL_TLS_REQUIRED, authType, username, password,
                        clientCertificateAlias);
                mAccount.setOutgoingServerSettings(transportServer);

                AccountSetupOutgoing.actionOutgoingSettings(this, mAccount);
            }
        }
    }

    protected void onNext() {
        try {
            ConnectionSecurity connectionSecurity = getSelectedSecurity();

            String username = mUsernameView.getText().toString().trim();
            String password = null;
            String clientCertificateAlias = null;

            AuthType authType = getSelectedAuthType();

            if ((ConnectionSecurity.SSL_TLS_REQUIRED == connectionSecurity) ||
                    (ConnectionSecurity.STARTTLS_REQUIRED == connectionSecurity) ) {
                clientCertificateAlias = mClientCertificateSpinner.getAlias();
            }
            if (authType != AuthType.EXTERNAL) {
                password = mPasswordView.getText().toString();
            }
            String host = mServerView.getText().toString();
            int port = Integer.parseInt(mPortView.getText().toString());

            Map<String, String> extra = emptyMap();
            if (mStoreType.equals(Protocols.IMAP)) {
                boolean autoDetectNamespace = mImapAutoDetectNamespaceView.isChecked();
                String pathPrefix = mImapPathPrefixView.getText().toString();
                extra = ImapStoreSettings.createExtra(autoDetectNamespace, pathPrefix);
            } else if (mStoreType.equals(Protocols.WEBDAV)) {
                String path = mWebdavPathPrefixView.getText().toString();
                String authPath = mWebdavAuthPathView.getText().toString();
                String mailboxPath = mWebdavMailboxPathView.getText().toString();
                extra = WebDavStoreSettings.createExtra(null, path, authPath, mailboxPath);
            }

            DI.get(LocalKeyStoreManager.class).deleteCertificate(mAccount, host, port, MailServerDirection.INCOMING);
            ServerSettings settings = new ServerSettings(mStoreType, host, port,
                    connectionSecurity, authType, username, password, clientCertificateAlias, extra);

            mAccount.setIncomingServerSettings(settings);

            mAccount.setUseCompression(useCompressionCheckBox.isChecked());
            mAccount.setSubscribedFoldersOnly(mSubscribedFoldersOnly.isChecked());

            AccountSetupCheckSettings.actionCheckSettings(this, mAccount, CheckDirection.INCOMING);
        } catch (Exception e) {
            failure(e);
        }

    }

    public void onClick(View v) {
        try {
            if (v.getId() == R.id.next) {
                onNext();
            }
        } catch (Exception e) {
            failure(e);
        }
    }

    private void failure(Exception use) {
        Timber.e(use, "Failure");
        String toastText = getString(R.string.account_setup_bad_uri, use.getMessage());

        Toast toast = Toast.makeText(getApplication(), toastText, Toast.LENGTH_LONG);
        toast.show();
    }


    /*
     * Calls validateFields() which enables or disables the Next button
     * based on the fields' validity.
     */
    TextWatcher validationTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            validateFields();
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            /* unused */
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            /* unused */
        }
    };

    OnClientCertificateChangedListener clientCertificateChangedListener = alias -> validateFields();

    private AuthType getSelectedAuthType() {
        AuthTypeHolder holder = (AuthTypeHolder) mAuthTypeView.getSelectedItem();
        return holder.authType;
    }

    private ConnectionSecurity getSelectedSecurity() {
        ConnectionSecurityHolder holder = (ConnectionSecurityHolder) mSecurityTypeView.getSelectedItem();
        return holder.connectionSecurity;
    }
}
