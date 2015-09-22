package com.android;

import com.android.todolist.R;
import com.redux.AppAction;
import com.redux.ActionCreator;
import com.redux.AppState;
import com.redux.Store;
import com.redux.Subscriber;
import com.redux.Subscription;
import com.redux.Todo;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


public class TodoActivity extends BaseActivity implements Subscriber, SwipeRefreshLayout.OnRefreshListener {
    private static final String EMPTY_STRING = "";

    private final TextView.OnEditorActionListener onTextSendAddTodo = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            if (isDone(actionId)) {
                actionCreator.add(view.getText().toString());
                reset(view);
                return true;
            }
            return false;
        }

        private boolean isDone(int actionId) {
            return actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_UNSPECIFIED;
        }

        private void reset(TextView view) {
            view.setText(EMPTY_STRING);
            recyclerView.requestFocus();
            platformUtils.hideKeyboard(view);
        }
    };

    private final CompoundButton.OnCheckedChangeListener onCheckedMarkTodo = new CompoundButton.OnCheckedChangeListener() {
        @Override public void onCheckedChanged(CompoundButton compoundButton, boolean isMarked) {
            final int id = (int) compoundButton.getTag();
            actionCreator.complete(id, isMarked);
        }
    };

    private final CompoundButton.OnCheckedChangeListener onCheckedMarkAll = new CompoundButton.OnCheckedChangeListener() {
        @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            actionCreator.completeAll(isChecked);
        }
    };

    private final View.OnClickListener onClickClearMarked = new View.OnClickListener() {
        @Override public void onClick(View v) {
            actionCreator.clearCompleted();
        }
    };

    private final View.OnClickListener onClickDeleteTodo = new View.OnClickListener() {
        @Override public void onClick(View v) {
            final int id = (int) v.getTag();
            actionCreator.delete(id);
        }
    };

    @Inject Store<AppAction, AppState> store;
    @Inject ActionCreator actionCreator;
    @Inject PlatformUtils platformUtils;
    private Subscription subscription;
    private MyAdapter adapter;
    private RecyclerView recyclerView;
    private EditText editText;
    private CheckBox markAllView;
    private Button clearAllMarked;
    private SwipeRefreshLayout swipeLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);

        swipeLayout = (SwipeRefreshLayout) findViewById(R.id.pull_refresh_container);
        swipeLayout.setOnRefreshListener(this);
        setupRecycler();
        setupEditText();
        setUpMarkAll();
        setupClearAllMarked();
        recyclerView.requestFocus();
    }

    @Override public void onRefresh() {
        actionCreator.fetch();
    }

    private void setUpMarkAll() {
        markAllView = (CheckBox) findViewById(R.id.mark_all);
        markAllView.setOnCheckedChangeListener(onCheckedMarkAll);
    }

    private void setupEditText() {
        editText = (EditText) findViewById(R.id.todo_create);
        editText.setOnEditorActionListener(onTextSendAddTodo);
    }

    private void setupClearAllMarked() {
        clearAllMarked = (Button) findViewById(R.id.clear_all_marked);
        clearAllMarked.setOnClickListener(onClickClearMarked);
    }

    private void setupRecycler() {
        final ArrayList<Todo> todoList = new ArrayList<>(store.getState().getList());
        adapter = new MyAdapter(todoList, getResources(), onCheckedMarkTodo, onClickDeleteTodo);
        recyclerView = (RecyclerView) findViewById(R.id.todo_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        platformUtils.showKeyboard(editText);
        bind();
        subscription = store.subscribe(this);
    }

    @Override protected void onPause() {
        subscription.unsubscribe();
        super.onPause();
    }

    @Override public void onStateChanged() {
        bind();
    }

    private void bind() {
        swipeLayout.setRefreshing(store.getState().getIsFetching());

        final List<Todo> todoList = store.getState().getList();
        markAllView.setEnabled(!todoList.isEmpty());
        markAllView.setOnCheckedChangeListener(null);
        markAllView.setChecked(!todoList.isEmpty() && isAllCompleted(todoList));
        markAllView.setOnCheckedChangeListener(onCheckedMarkAll);

        clearAllMarked.setEnabled(!todoList.isEmpty() && hasAtLeastOneComplete(todoList));
        adapter.setTodoList(new ArrayList<>(todoList));
        adapter.notifyDataSetChanged();
    }

    private boolean hasAtLeastOneComplete(List<Todo> todoList) {
        for (Todo todo : todoList) {
            if (todo.getIsCompleted()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllCompleted(List<Todo> todoList) {
        for (Todo todo : todoList) {
            if ((!todo.getIsCompleted())) {
                return false;
            }
        }
        return true;
    }

    private static class MyAdapter extends RecyclerView.Adapter<MyViewHolder> {

        private final View.OnClickListener onClickDeleteTodo;
        private List<Todo> todoList;
        private final Resources resources;
        private final CompoundButton.OnCheckedChangeListener onMarkedListener;

        public MyAdapter(List<Todo> todoList, Resources resources, CompoundButton.OnCheckedChangeListener onMarkedListener, View.OnClickListener onClickDeleteTodo) {
            this.todoList = todoList;
            this.resources = resources;
            this.onMarkedListener = onMarkedListener;
            this.onClickDeleteTodo = onClickDeleteTodo;
        }

        @Override public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new MyViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.todo_item, parent, false));
        }

        @Override public void onBindViewHolder(MyViewHolder holder, int position) {
            final Todo todo = todoList.get(position);
            final CheckBox checkBox = (CheckBox) holder.itemView.findViewById(R.id.todo_item);
            checkBox.setTag(todo.getId());
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setText(todo.getText());
            checkBox.setChecked(todo.getIsCompleted());
            checkBox.setTextColor(todo.getIsCompleted() ? resources.getColor(R.color.task_done) : resources.getColor(R.color.task_todo));
            checkBox.setOnCheckedChangeListener(onMarkedListener);

            final Button deleteButton = (Button) holder.itemView.findViewById(R.id.todo_clear);
            deleteButton.setTag(todo.getId());
            deleteButton.setText(todo.getIsCompleted() ? resources.getString(R.string.clear) : resources.getString(R.string.delete));
            deleteButton.setOnClickListener(onClickDeleteTodo);
        }

        @Override public int getItemCount() {
            return todoList.size();
        }

        public void setTodoList(List<Todo> todoList) {
            this.todoList = todoList;
        }
    }

    private static class MyViewHolder extends RecyclerView.ViewHolder {
        public MyViewHolder(View itemView) {
            super(itemView);
        }
    }
}