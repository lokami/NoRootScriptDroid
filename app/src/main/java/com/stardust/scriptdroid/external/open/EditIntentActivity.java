package com.stardust.scriptdroid.external.open;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;

import com.stardust.scriptdroid.ui.BaseActivity;
import com.stardust.scriptdroid.ui.edit.EditActivity;
import com.stardust.scriptdroid.R;

/**
 * Created by Stardust on 2017/2/2.
 */

public class EditIntentActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handleIntent();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.edit_and_run_handle_intent_error, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private void handleIntent() {
        Intent intent = getIntent();
        String path = intent.getData().getPath();
        if (!TextUtils.isEmpty(path))
            EditActivity.editFile(this, path);
    }
}
