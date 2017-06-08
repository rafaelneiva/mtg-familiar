package com.gelakinetic.mtgfam.fragments;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.gelakinetic.mtgfam.R;
import com.gelakinetic.mtgfam.helpers.PreferenceAdapter;
import com.gelakinetic.mtgfam.helpers.database.CardDbAdapter;
import com.gelakinetic.mtgfam.helpers.database.DatabaseManager;
import com.gelakinetic.mtgfam.helpers.database.FamiliarDbException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by xvicarious on 5/24/17.
 */

public abstract class FamiliarListFragment extends FamiliarFragment {

    /* Preferences */
    public int mPriceSetting;

    /* Pricing Constants */
    public static final int LOW_PRICE = 0;
    public static final int AVG_PRICE = 1;
    public static final int HIGH_PRICE = 2;

    /* UI Elements */
    public AutoCompleteTextView mNameField;
    public EditText mNumberOfField;
    public CheckBox mCheckboxFoil;
    TextView mTotalPriceField;

    int mPriceFetchRequests = 0;

    RecyclerView mListView;

    public CardDataAdapter mListAdapter;

    boolean mCheckboxFoilLocked = false;

    void initializeMembers(View fragmentView) {
        mNameField = (AutoCompleteTextView) fragmentView.findViewById(R.id.name_search);
        mNumberOfField = (EditText) fragmentView.findViewById(R.id.number_input);
        mCheckboxFoil = (CheckBox) fragmentView.findViewById(R.id.list_foil);
        mListView = (RecyclerView) fragmentView.findViewById(R.id.cardlist);
    }

    void setUpCheckBoxClickListeners() {

        mCheckboxFoil.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                /* Lock the checkbox on long click */
                mCheckboxFoilLocked = true;
                mCheckboxFoil.setChecked(true);
                return true;
            }
        });

        mCheckboxFoil.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    /* Unlock the checkbox when the user unchecks it */
                    mCheckboxFoilLocked = false;
                }
            }
        });

    }

    /**
     * @return a callback helper for swipe to delete
     */
    ItemTouchHelper getTouchHelper() {
                /* A Callback to detect if our item is being swiped away */
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            Drawable background;
            Drawable xMark;
            int xMarkMargin;
            boolean initiated;

            /**
             * Initialize the variables used in the callback
             */
            private void init() {
                background = new ColorDrawable(Color.RED);
                xMark = ContextCompat.getDrawable(getContext(), R.drawable.ic_close_dark);
                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                xMarkMargin = 10; // todo: actually make this dimension
            }

            /** This is unused */
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            /**
             * The item is swiped
             * @param viewHolder what is swiped
             * @param direction direction of what was swiped
             */
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int swipedPosition = viewHolder.getAdapterPosition();
                mListAdapter.pendingRemoval(swipedPosition);
                mListAdapter.notifyDataSetChanged();
            }

            /**
             * Upon drawing the item
             * @param canvas canvas to draw on
             * @param recyclerView the parent
             * @param viewHolder what holder is being drawn on
             * @param dX see super
             * @param dY see super
             * @param actionState see super
             * @param isCurrentlyActive see super
             */
            @Override
            public void onChildDraw(Canvas canvas, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                /* Sometimes this is called even if the item is gone */
                if (viewHolder.getAdapterPosition() == -1) {
                    return;
                }

                /* If the user aborts the swipe, don't go through the drawing */
                if (!isCurrentlyActive) {
                    return;
                }

                if (!initiated) {
                    init();
                }

                View itemView = viewHolder.itemView;

                background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                background.draw(canvas);
                int itemHeight = itemView.getBottom() - itemView.getTop();
                int intrinsicWidth = xMark.getIntrinsicWidth();
                int intrinsicHeight = xMark.getIntrinsicHeight();
                int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
                int xMarkRight = itemView.getRight() - xMarkMargin;
                int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
                int xMarkBottom = xMarkTop + intrinsicHeight;
                xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);
                xMark.draw(canvas);
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            /**
             * Get the direction that an item can swipe
             * @param parent the parent
             * @param holder holder being swiped
             * @return what directions the item can be swiped
             */
            @Override
            public int getSwipeDirs(RecyclerView parent, RecyclerView.ViewHolder holder) {
                if (!((CardDataAdapter.ViewHolder) holder).swipeable) {
                    /* If swipeable is false, return 0 */
                    return 0;
                }
                /* Otherwise the default */
                return super.getSwipeDirs(parent, holder);
            }

        };
        return new ItemTouchHelper(simpleItemTouchCallback);
    }

    /**
     * Create the item decoration for swipe to delete
     * @return a pretty animation thing
     */
    RecyclerView.ItemDecoration getItemDecorator() {
        return new RecyclerView.ItemDecoration() {

            Drawable background;
            boolean initiated;

            /**
             * Setup the drawables
             */
            private void init() {
                background = new ColorDrawable(Color.RED);
                initiated = true;
            }

            /**
             * @param canvas the cavas to draw on
             * @param parent parent of the drawn item
             * @param state see super
             */
            @Override
            public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
                if (!initiated) {
                    init();
                }
                /* Only do it if the animation is running */
                if (parent.getItemAnimator().isRunning()) {
                    View lastViewComingDown = null;
                    View firstViewComingUp = null;
                    int left = 0;
                    int right = parent.getWidth();
                    int top = 0;
                    int bottom = 0;
                    int childCount = parent.getLayoutManager().getChildCount();
                    /* get the children above and below the removed item */
                    for (int i = 0; i < childCount; i++) {
                        View child = parent.getLayoutManager().getChildAt(i);
                        if (child.getTranslationY() < 0) {
                            lastViewComingDown = child;
                        } else if (child.getTranslationY() > 0
                                && firstViewComingUp == null) {
                            firstViewComingUp = child;
                        }
                    }
                    /* set the tops and bottoms for the animation */
                    if (lastViewComingDown != null && firstViewComingUp != null) {
                        top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
                        bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
                    } else if (lastViewComingDown != null) {
                        top = lastViewComingDown.getBottom() + (int) lastViewComingDown.getTranslationY();
                        bottom = lastViewComingDown.getBottom();
                    } else if (firstViewComingUp != null) {
                        top = firstViewComingUp.getTop();
                        bottom = firstViewComingUp.getTop() + (int) firstViewComingUp.getTranslationY();
                    }
                    background.setBounds(left, top, right, bottom);
                    background.draw(canvas);
                }
                super.onDraw(canvas, parent, state);
            }

        };
    }

    @Override
    public void onPause() {
        super.onPause();
        mListAdapter.removePendingNow();
    }

    /**
     * Receive the result from the card image search, then fill in the name edit text on the
     * UI thread
     *
     * @param multiverseId The multiverseId of the card the query returned
     */
    @Override
    public void receiveTutorCardsResult(long multiverseId) {
        SQLiteDatabase database = DatabaseManager.getInstance(getActivity(), false)
                .openDatabase(false);
        try {
            Cursor card = CardDbAdapter.fetchCardByMultiverseId(multiverseId, new String[]{
                    CardDbAdapter.DATABASE_TABLE_CARDS + "." + CardDbAdapter.KEY_NAME}, database);
            final String name = card.getString(card.getColumnIndex(CardDbAdapter.KEY_NAME));
            getFamiliarActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mNameField.setText(name);
                }
            });
            card.close();
        } catch (FamiliarDbException e) {
            e.printStackTrace();
        }
        DatabaseManager.getInstance(getActivity(), false).closeDatabase(false);
    }

    public abstract class CardDataAdapter<E> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        ArrayList<E> mItems;
        ArrayList<E> mItemsPendingRemoval;

        SparseBooleanArray mSelectedItems;

        Handler mHandler;
        HashMap<E, Runnable> mPendingRunnables;

        View.OnClickListener mClickListener;
        View.OnLongClickListener mLongClickListener;

        private boolean mSelectMode;

        CardDataAdapter(ArrayList<E> values) {
            mItems = values;
            mItemsPendingRemoval = new ArrayList<>();
            mHandler = new Handler();
            mPendingRunnables = new HashMap<>();
            mSelectMode = false;
            mSelectedItems = new SparseBooleanArray();
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        /**
         * Where things go before they get removed
         * @param position where the item to be removed is
         */
        void pendingRemoval(int position) {
            final E item = mItems.get(position);
            if (!mItemsPendingRemoval.contains(item)) {
                mItemsPendingRemoval.add(item);
                notifyItemChanged(position);
                Runnable pendingRemovalRunnable = new Runnable() {
                    @Override
                    public void run() {
                        remove(mItems.indexOf(item));
                    }
                };
                PreferenceAdapter pa = new PreferenceAdapter(getContext());
                mHandler.postDelayed(pendingRemovalRunnable, pa.getUndoTimeout());
                mPendingRunnables.put(item, pendingRemovalRunnable);
                Snackbar undoBar = Snackbar.make(getFamiliarActivity().findViewById(R.id.fragment_container),
                        /* todo: Actual text for this part.
                         * Possibly make it so the action says the card's name? */
                        R.string.app_name, Snackbar.LENGTH_SHORT);
                undoBar.setAction(R.string.cardlist_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Runnable pendingRemovalRunnable = mPendingRunnables.get(item);
                        mPendingRunnables.remove(item);
                        if (pendingRemovalRunnable != null) {
                            mHandler.removeCallbacks(pendingRemovalRunnable);
                        }
                        mItemsPendingRemoval.remove(item);
                        notifyItemChanged(mItems.indexOf(item));
                    }
                });
                undoBar.show();
            }
        }

        /**
         * Execute any pending runnables NOW. This generally means we are moving away from this
         * fragment
         */
        void removePendingNow() {
            Iterator iterator = mPendingRunnables.entrySet().iterator();
            while (iterator.hasNext()) {
                Runnable runnable = (Runnable) ((Map.Entry) iterator.next()).getValue();
                mHandler.removeCallbacks(runnable);
                runnable.run();
            }
            mPendingRunnables.clear();
        }

        /**
         * Properly go about removing an item from the list
         * @param position where the item to remove is
         */
        public void remove(int position) {
            try {
                final E item = mItems.get(position);
                if (mItemsPendingRemoval.contains(item)) {
                    mItemsPendingRemoval.remove(item);
                }
                if (mItems.contains(item)) {
                    mItems.remove(item);
                    /* The items that change are including and after position */
                    notifyItemRemoved(position);
                }
            } catch (ArrayIndexOutOfBoundsException aob) {
                /* Eat it, because who cares? */
            }
        }

        /**
         * If we are in select mode
         * @return mSelectMode
         */
        public boolean getSelectMode() {
            return mSelectMode;
        }

        /**
         * Set if we are in select mode
         * @param isOn if we should be in select mode or not
         */
        public void setSelectMode(final boolean isOn) {
            mSelectMode = isOn;
        }

        public ArrayList<E> getSelectedItems() {
            final ArrayList<E> selectedItems = new ArrayList<>();
            for (int i = 0; i < mSelectedItems.size(); i++) {
                if (mSelectedItems.valueAt(i)) {
                    selectedItems.add(mItems.get(mSelectedItems.keyAt(i)));
                }
            }
            return selectedItems;
        }

        /**
         * Deselect all items
         */
        public void unselectAll() {
            mSelectedItems.clear();
            setSelectMode(false);
            notifyDataSetChanged();
        }

        abstract class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

            TextView mCardName;

            boolean swipeable = true;

            ViewHolder(ViewGroup view, @LayoutRes int listRowLayout) {
                super(LayoutInflater.from(view.getContext()).inflate(listRowLayout, view, false));
                mCardName = (TextView) itemView.findViewById(R.id.card_name);
            }

            @Override
            public void onClick(View view) {
                if (getSelectMode()) {
                    if (mSelectedItems.get(getAdapterPosition(), false)) {
                        mSelectedItems.delete(getAdapterPosition());
                        itemView.setSelected(false);
                        if (mSelectedItems.size() < 1) {
                            setSelectMode(false);
                            getFamiliarActivity().invalidateOptionsMenu();
                        }
                        return;
                    }
                    itemView.setSelected(true);
                    mSelectedItems.put(getAdapterPosition(), true);
                }
            }

            @Override
            public boolean onLongClick(View view) {
                if (!getSelectMode()) {
                    itemView.setSelected(true);
                    mSelectedItems.put(getAdapterPosition(), true);
                    setSelectMode(true);
                    getFamiliarActivity().invalidateOptionsMenu();
                    return true;
                }
                return false;
            }

        }

    }

}