package com.stardust.scriptdroid.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.DialogUtils;
import com.stardust.pio.PFile;
import com.stardust.pio.PFiles;
import com.stardust.pio.UncheckedIOException;
import com.stardust.scriptdroid.App;
import com.stardust.scriptdroid.R;
import com.stardust.scriptdroid.io.TmpScriptFiles;
import com.stardust.scriptdroid.model.script.ScriptFile;
import com.stardust.scriptdroid.model.script.Scripts;
import com.stardust.scriptdroid.io.StorageFileProvider;
import com.stardust.scriptdroid.model.sample.Sample;
import com.stardust.scriptdroid.network.download.DownloadManager;
import com.stardust.scriptdroid.ui.filechooser.FileChooserDialogBuilder;
import com.stardust.scriptdroid.ui.shortcut.ShortcutCreateActivity;
import com.stardust.theme.dialog.ThemeColorMaterialDialogBuilder;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;


/**
 * Created by Stardust on 2017/7/31.
 */

public class ScriptOperations {

    private static final String LOG_TAG = "ScriptOperations";
    private Context mContext;
    private View mView;
    private ScriptFile mCurrentDirectory;
    private StorageFileProvider mStorageFileProvider;

    public ScriptOperations(Context context, View view, ScriptFile currentDirectory) {
        mContext = context;
        mView = view;
        mCurrentDirectory = currentDirectory;
        mStorageFileProvider = StorageFileProvider.getDefault();
    }

    public ScriptOperations(Context context, View view) {
        this(context, view, new ScriptFile(StorageFileProvider.DEFAULT_DIRECTORY));
    }

    public void newScriptFileForScript(final String script) {
        showFileNameInputDialog("", "js")
                .subscribe(input ->
                        createScriptFile(getCurrentDirectoryPath() + input + ".js", script, false)
                );
    }

    private String getCurrentDirectoryPath() {
        return getCurrentDirectory().getPath() + "/";
    }

    private ScriptFile getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public void createScriptFile(String path, String script, boolean edit) {
        if (PFiles.createIfNotExists(path)) {
            if (script != null) {
                try {
                    PFiles.write(path, script);
                } catch (UncheckedIOException e) {
                    showMessage(R.string.text_file_write_fail);
                    return;
                }
            }
            mStorageFileProvider.notifyFileCreated(mCurrentDirectory, new ScriptFile(path));
            if (edit)
                Scripts.edit(path);
        } else {
            showMessage(R.string.text_create_fail);
        }
    }

    public void newScriptFile() {
        showFileNameInputDialog("", "js")
                .subscribe(input -> createScriptFile(getCurrentDirectoryPath() + input + ".js", null, true));
    }

    public Observable<String> importFile(final String pathFrom) {
        return showFileNameInputDialog(PFiles.getNameWithoutExtension(pathFrom), PFiles.getExtension(pathFrom))
                .observeOn(Schedulers.io())
                .map(new Function<String, String>() {
                    @Override
                    public String apply(@io.reactivex.annotations.NonNull String s) throws Exception {
                        final String pathTo = getCurrentDirectoryPath() + s + "." + PFiles.getExtension(pathFrom);
                        if (PFiles.copy(pathFrom, pathTo)) {
                            showMessage(R.string.text_import_succeed);
                        } else {
                            showMessage(R.string.text_import_fail);
                        }
                        mStorageFileProvider.notifyFileCreated(mCurrentDirectory, new ScriptFile(pathTo));
                        return pathTo;
                    }
                });
    }

    public Observable<String> importFile(String prefix, final InputStream inputStream, final String ext) {
        return showFileNameInputDialog(PFiles.getNameWithoutExtension(prefix), ext)
                .observeOn(Schedulers.io())
                .map(new Function<String, String>() {
                    @Override
                    public String apply(@io.reactivex.annotations.NonNull String s) throws Exception {
                        final String pathTo = getCurrentDirectoryPath() + s + "." + ext;
                        if (PFiles.copyStream(inputStream, pathTo)) {
                            showMessage(R.string.text_import_succeed);
                        } else {
                            showMessage(R.string.text_import_fail);
                        }
                        mStorageFileProvider.notifyFileCreated(mCurrentDirectory, new ScriptFile(pathTo));
                        return pathTo;
                    }
                });
    }


    public void newDirectory() {
        showNameInputDialog("", new InputCallback())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull String path) throws Exception {
                        if (new ScriptFile(getCurrentDirectory(), path).mkdirs()) {
                            showMessage(R.string.text_already_create);
                            mStorageFileProvider.notifyFileCreated(mCurrentDirectory, new ScriptFile(path));
                        } else {
                            showMessage(R.string.text_create_fail);
                        }
                    }
                });
    }

    private void showMessage(final int resId) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showMessageWithoutThreadSwitch(resId);
        }
        //switch to ui thread to show message
        App.getApp().getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                showMessageWithoutThreadSwitch(resId);
            }
        });
    }

    private void showMessageWithoutThreadSwitch(int resId) {
        if (mView != null) {
            Snackbar.make(mView, resId, Snackbar.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mContext, resId, Toast.LENGTH_SHORT).show();
        }
    }


    private Observable<String> showFileNameInputDialog(String prefix, String ext) {
        return showNameInputDialog(prefix, new InputCallback(ext));
    }

    private Observable<String> showNameInputDialog(String prefix, MaterialDialog.InputCallback textWatcher) {
        final PublishSubject<String> input = PublishSubject.create();
        DialogUtils.showDialog(new ThemeColorMaterialDialogBuilder(mContext).title(R.string.text_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .alwaysCallInputCallback()
                .input(getString(R.string.text_please_input_name), prefix, false, textWatcher)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        input.onNext(dialog.getInputEditText().getText().toString());
                        input.onComplete();
                    }
                })
                .build());
        return input;
    }


    private CharSequence getString(int resId) {
        return mContext.getString(resId);
    }

    public Observable<String> importSample(Sample sample) {
        try {
            return importFile(sample.name, mContext.getAssets().open(sample.path), PFiles.getExtension(sample.path));
        } catch (IOException e) {
            e.printStackTrace();
            showMessage(R.string.text_import_fail);
            return Observable.error(e);
        }
    }

    public Observable<Boolean> rename(final ScriptFile file) {
        final ScriptFile oldFile = new ScriptFile(file.getPath());
        String originalName = file.getSimplifiedName();
        return showNameInputDialog(originalName, new InputCallback(file.isDirectory() ? null : PFiles.getExtension(file.getName()),
                originalName))
                .map(newName -> {
                    PFile newFile = file.renameAndReturnNewFile(newName);
                    if (newFile != null) {
                        mStorageFileProvider.notifyFileChanged(mCurrentDirectory, oldFile, newFile);
                    }
                    return newFile != null;
                });
    }

    public void createShortcut(ScriptFile file) {
        mContext.startActivity(new Intent(mContext, ShortcutCreateActivity.class)
                .putExtra(ShortcutCreateActivity.EXTRA_FILE, file));
    }

    public void delete(final ScriptFile scriptFile) {
        Observable.fromPublisher(new Publisher<Boolean>() {
            @Override
            public void subscribe(Subscriber<? super Boolean> s) {
                s.onNext(PFiles.deleteRecursively(scriptFile));
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> {
                    showMessage(deleted ? R.string.text_already_delete : R.string.text_delete_failed);
                    if (deleted)
                        mStorageFileProvider.notifyFileRemoved(mCurrentDirectory, scriptFile);
                });
    }

    public Observable<ScriptFile> download(String url) {
        String fileName = DownloadManager.parseFileNameLocally(url);
        return new FileChooserDialogBuilder(mContext)
                .title(R.string.text_select_save_path)
                .chooseDir()
                .singleChoice()
                .map(saveDir -> new File(saveDir, fileName).getPath())
                .flatMap(savePath -> {
                    if (!new File(savePath).exists()) {
                        return Observable.just(savePath);
                    }
                    return RxDialogs.confirm(mContext, R.string.confirm_overwrite_file)
                            .flatMap(yes -> {
                                if (yes) {
                                    new File(savePath).delete();
                                    return Observable.just(savePath);
                                } else {
                                    return Observable.empty();
                                }
                            });
                })
                .flatMap(savePath -> download(url, savePath, createDownloadProgressDialog(url, fileName)));
    }

    private MaterialDialog createDownloadProgressDialog(String url, String fileName) {
        return new MaterialDialog.Builder(mContext)
                .progress(false, 100)
                .title(fileName)
                .cancelable(false)
                .positiveText(R.string.text_cancel_download)
                .onPositive((dialog, which) -> DownloadManager.getInstance(mContext).cancelDownload(url))
                .show();
    }

    public Observable<ScriptFile> download(String url, String path, MaterialDialog progressDialog) {
        PublishSubject<ScriptFile> subject = PublishSubject.create();
        DownloadManager.getInstance(mContext).download(url, path)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(progressDialog::setProgress)
                .doOnComplete(() -> {
                    progressDialog.dismiss();
                    subject.onNext(new ScriptFile(path));
                    subject.onComplete();
                })
                .doOnError(error -> {
                    Log.e(LOG_TAG, "Download failed", error);
                    progressDialog.dismiss();
                    showMessage(R.string.text_download_failed);
                    subject.onError(error);
                })
                .subscribe();
        return subject;
    }

    public Observable<ScriptFile> temporarilyDownload(String url) {
        String fileName = DownloadManager.parseFileNameLocally(url);
        return Observable.fromCallable(() -> TmpScriptFiles.create(mContext))
                .flatMap(tmpFile ->
                        download(url, tmpFile.getPath(), createDownloadProgressDialog(url, fileName)));
    }

    public void importFile() {
        new FileChooserDialogBuilder(mContext)
                .dir(Environment.getExternalStorageDirectory().getPath())
                .justScriptFile()
                .singleChoice(file -> importFile(file.getPath()))
                .title(R.string.text_select_file_to_import)
                .positiveText(R.string.ok)
                .show();
    }


    private class InputCallback implements MaterialDialog.InputCallback {

        private String mExcluded;
        private boolean mIsFirstTextChanged = true;
        private String mExtension;

        InputCallback(@Nullable String ext, String excluded) {
            mExtension = "." + ext;
            mExcluded = excluded;
        }

        InputCallback(String ext) {
            this(ext, null);
        }

        public InputCallback() {
            this(null);
        }

        @Override
        public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
            if (mIsFirstTextChanged) {
                mIsFirstTextChanged = false;
                return;
            }
            EditText editText = dialog.getInputEditText();
            if (editText == null)
                return;
            int errorResId = 0;
            if (input == null || input.length() == 0) {
                errorResId = R.string.text_name_should_not_be_empty;
            } else if (!input.equals(mExcluded)) {
                if (new File(getCurrentDirectory(), mExtension == null ? input.toString() : input.toString() + mExtension).exists()) {
                    errorResId = R.string.text_file_exists;
                }
            }
            if (errorResId == 0) {
                editText.setError(null);
                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
            } else {
                editText.setError(getString(errorResId));
                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
            }

        }
    }


}
