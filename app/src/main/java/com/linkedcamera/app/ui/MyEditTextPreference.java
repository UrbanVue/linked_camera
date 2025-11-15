package com.linkedcamera.app.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.linkedcamera.app.R;

/** This contains a custom preference for an EditTextPreference. We do all this to fix the problem
 *  that Android's EditTextPreference doesn't satisfy Google's own emoji policy, due to the
 *  programmatically allocated EditText (which means AppCompat can't update it to support emoji
 *  properly). This is fixed with AndroidX (androidx.preference.*), but switching to that is a major
 *  change.
 *  Once we have switched to AndroidX's preference libraries, we can switch back to
 *  EditTextPreference (but check that the emoji strings still work on Android 10 or earlier!)
 */
public class MyEditTextPreference extends DialogPreference {
    //private static final String TAG = "MyEditTextPreference";

    private EditText edittext;

    private String dialogMessage = "";
    private final int inputType;

    private String value; // current saved value of this preference (note that this is intentionally not updated when the seekbar changes, as we don't save until the user clicks ok)
    private boolean value_set;

    public MyEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        String namespace = "http://schemas.android.com/apk/res/android";

        // can't get both strings and resources to work - only support resources
        int id = attrs.getAttributeResourceValue(namespace, "dialogMessage", 0);
        if( id > 0 )
            this.dialogMessage = context.getString(id);

        this.inputType = attrs.getAttributeIntValue(namespace, "inputType", EditorInfo.TYPE_NULL);

        setDialogLayoutResource(R.layout.myedittextpreference);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        this.edittext = view.findViewById(R.id.myedittextpreference_edittext);
        this.edittext.setInputType(inputType);

        TextView textView = view.findViewById(R.id.myedittextpreference_summary);
        textView.setText(dialogMessage);

        if( value != null ) {
            this.edittext.setText(value);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if( positiveResult ) {
            String new_value = edittext.getText().toString();
            if( callChangeListener(new_value) ) {
                setValue(new_value);
            }
        }
    }

    public String getText() {
        return value;
    }

    private void setValue(String value) {
        final boolean changed = !TextUtils.equals(this.value, value);
        if( changed || !value_set ) {
            this.value = value;
            value_set = true;
            persistString(value);
            if( changed ) {
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(value) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if( isPersistent() ) {
            return superState;
        }

        final SavedState state = new SavedState(superState);
        state.value = value;
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if( state == null || !state.getClass().equals(SavedState.class) ) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState)state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    private static class SavedState extends BaseSavedState {
        String value;

        SavedState(Parcel source) {
            super(source);
            value = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
