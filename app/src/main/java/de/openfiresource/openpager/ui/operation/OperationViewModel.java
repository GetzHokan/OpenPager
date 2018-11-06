package de.openfiresource.openpager.ui.operation;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.LiveDataReactiveStreams;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.openfiresource.openpager.models.AppDatabase;
import de.openfiresource.openpager.models.database.OperationMessage;
import de.openfiresource.openpager.utils.TimeHelper;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class OperationViewModel extends ViewModel {

    private static final String TAG = "OperationViewModel";

    private final WeakReference<Context> context;

    private final MutableLiveData<Long> operationId = new MutableLiveData<>();

    private final LiveData<OperationMessage> operation;

    private final LiveData<String> timer;

    @Inject
    OperationViewModel(final @NonNull Context context, final @NonNull AppDatabase database) {
        this.context = new WeakReference<>(context);
        this.operation = Transformations.switchMap(operationId, id -> LiveDataReactiveStreams.fromPublisher(
                database.operationMessageDao().findByIdAsync(id).map(operation -> {
                    if (operation.getOperationRuleId() != null) {
                        operation.setOperationRule(database.operationRuleDao().findById(operation.getOperationRuleId()));
                    }
                    return operation;
                }))
        );
        this.timer = Transformations.switchMap(operation, operation -> LiveDataReactiveStreams.fromPublisher(createTimer(operation)));

        Log.d(TAG, "OperationViewModel() called");
    }

    public LiveData<OperationMessage> getOperation() {
        return operation;
    }

    public LiveData<String> getTimer() {
        return timer;
    }

    void setOperationId(long operationId) {
        this.operationId.postValue(operationId);
    }

    private Flowable<String> createTimer(OperationMessage operation) {
        return Flowable
                .interval(0, 1, TimeUnit.SECONDS, Schedulers.io())
                .switchMapSingle(interval ->
                        Single.create(
                                emitter -> emitter.onSuccess(TimeHelper.getDiffText(context.get(), operation.getTimestamp()))
                        )
                );
    }

    @Override
    protected void onCleared() {
        Log.d(TAG, "onCleared: ");
    }
}