package com.github.marco9999.uwatimetable;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by msatti on 12/04/16.
 */
class HelperTimetableDatabase extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "timetable.db";

    private SQLiteDatabase database = null;

    UtilFragment utilFragment;

    HelperTimetableDatabase(UtilFragment utilFragment) {
        super(utilFragment.getContext(), DATABASE_NAME, null, DATABASE_VERSION);
        this.utilFragment = utilFragment;
    }

    ////////////////////
    // SQL functions. //
    ////////////////////

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ContractTimetableDatabase.SQL_CREATE_COMMAND);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(ContractTimetableDatabase.SQL_DROP_COMMAND);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    boolean openDB() {
        boolean isOpen = false;
        try {
            database = getWritableDatabase();
            if (database == null) throw new NullPointerException("HelperTimetableDatabase: openDB: getWritableDatabase() returned null!");
            isOpen = true;
        }
        catch (Exception ex) {
            Log.d(Tag.LOG, "HelperTimetableDatabase: openDB: Failed to open database");
            Log.d(Tag.LOG, ex.getLocalizedMessage());
        }
        return isOpen;
    }

    void closeDB() {
        database.close();
        database = null;
    }

    void recreateDB() {
        if (database != null) {
            database.execSQL(ContractTimetableDatabase.SQL_DROP_COMMAND);
            database.execSQL(ContractTimetableDatabase.SQL_CREATE_COMMAND);
        }
    }

    SQLiteDatabase getDB() {
        return database;
    }

    ///////////////////////////
    // Data Check functions. //
    ///////////////////////////

    void checkEntryData_Day(HolderTimetableEntry entry) {
        final String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
        for (String day : days) {
            if (entry.get(ContractTimetableDatabase.COLUMN_CLASS_DAY).equals(day)) {
                return;
            }
        }

        throw new IllegalArgumentException("Trying to write entry to database with incorrect Day format. Requires debugging!");
    }

    void checkEntryData_Times(HolderTimetableEntry entry) {
        if (entry.get(ContractTimetableDatabase.COLUMN_CLASS_START_TIME).matches("\\d\\d:\\d\\d")
                && entry.get(ContractTimetableDatabase.COLUMN_CLASS_END_TIME).matches("\\d\\d:\\d\\d")) {
            return;
        }

        throw new IllegalArgumentException("Trying to write entry to database with incorrect Start or End Time format. Requires debugging!");
    }

    void checkEntryData_All(HolderTimetableEntry entry) {
        checkEntryData_Day(entry);
        checkEntryData_Times(entry);
    }

    //////////////////////////
    // Timetable functions. //
    //////////////////////////

    public enum SORT {
        NONE, START_TIME, DAY, DAY_THEN_START_TIME
    }

    private String orderBy(SORT sortType) {
        switch (sortType) {
            case NONE:
                return null;
            case START_TIME:
                return ("CAST(SUBSTR(" + ContractTimetableDatabase.COLUMN_CLASS_START_TIME + ",1,2) AS INTEGER) ASC");
            case DAY:
                return ("(CASE WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Sunday' THEN 1 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Monday' THEN 2 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Tuesday' THEN 3 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Wednesday' THEN 4 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Thursday' THEN 5 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Friday' THEN 6 "
                            + "WHEN " + ContractTimetableDatabase.COLUMN_CLASS_DAY + " = 'Saturday' THEN 7 ELSE 8 END)");
            case DAY_THEN_START_TIME:
                return (orderBy(SORT.DAY) + ", " + orderBy(SORT.START_TIME));
            default:
                return null;
        }
    }



    boolean writeTimetableDBEntry(HolderTimetableEntry info) {
        assert (database != null);
        // info contains list of class entry parameters (in the order listed by ContractTimetableDatabase.SET_COLUMN_NAMES) through the ContentValues object underneath it.
        // The ROWID (_ID) is automatically generated by the SQL engine.

        // Insert entry into timetable database.
        checkEntryData_All(info);
        long returnValue = database.insert(ContractTimetableDatabase.TABLE_NAME, null, info.getContentValues());

        // Return false if the insert function returned -1, which indicates an error.
        return !(returnValue == -1);
    }

    boolean writeTimetableDBEntryArray(HolderTimetableEntry[] infoArray) {
        assert (database != null);
        // info contains list of class entry parameters (in the order listed by ContractTimetableDatabase.SET_COLUMN_NAMES) through the ContentValues object underneath it.
        // The ROWID (_ID) is automatically generated by the SQL engine.

        // Insert entry into timetable database.
        boolean hasSucceeded = true;
        long returnValue;
        for (HolderTimetableEntry info : infoArray) {
            checkEntryData_All(info);
            returnValue = database.insert(ContractTimetableDatabase.TABLE_NAME, null, info.getContentValues());
            if (returnValue == -1) hasSucceeded = false;
        }

        // Return false if at least one insert function returned -1, which indicates an error.
        return hasSucceeded;
    }

    HolderTimetableEntry[] readTimetableDBEntry(SORT sortType, String dayParam, String weekParam) {
        Log.d(Tag.LOG, "Executing timetable database query with day = " + dayParam + ", week = " + weekParam + " and SORT = " + sortType.toString());

        // Format the day and week strings into SQL clauses. Check for appropriate SORT parameter.
        dayParam = Util.formatSQLDay(dayParam);
        weekParam = Util.formatSQLWeek(weekParam);
        if (dayParam == null) sortType = SORT.DAY_THEN_START_TIME;

        assert (database != null);
        // Get DB results.
        Cursor results = database.query(ContractTimetableDatabase.TABLE_NAME, null, dayParam, null, null, null, orderBy(sortType), null);

        // Allocate length of entryArray.
        HolderTimetableEntry[] entryArray = new HolderTimetableEntry[results.getCount()];

        // Put cursor results into holders.
        String[] tempStrArrayHolder;
        while (results.moveToNext()) {
            tempStrArrayHolder = new String[ContractTimetableDatabase.SET_COLUMN_NAMES_ID.length];
            for (int i = 0; i < ContractTimetableDatabase.SET_COLUMN_NAMES_ID.length; i++) {
                tempStrArrayHolder[i] =  results.getString(i);
            }
            entryArray[results.getPosition()] = new HolderTimetableEntry(tempStrArrayHolder, true);
        }

        // Close results cursor.
        results.close();

        Log.d(Tag.LOG, "Successfully executed database query. Number of entries = " + String.valueOf(entryArray.length));

        return entryArray;
    }

    HolderTimetableEntry[] readAllTimetableDBEntry(SORT sortType) {
        Log.d(Tag.LOG, "Executing timetable database query with SORT = " + sortType.toString());

        assert (database != null);
        // Get DB results.
        Cursor results = database.query(ContractTimetableDatabase.TABLE_NAME, null, null, null, null, null, orderBy(sortType), null);

        // Allocate length of entryArray.
        HolderTimetableEntry[] entryArray = new HolderTimetableEntry[results.getCount()];

        // Put cursor results into holders.
        String[] tempStrArrayHolder;
        while (results.moveToNext()) {
            tempStrArrayHolder = new String[ContractTimetableDatabase.SET_COLUMN_NAMES_ID.length];
            for (int i = 0; i < ContractTimetableDatabase.SET_COLUMN_NAMES_ID.length; i++) {
                tempStrArrayHolder[i] =  results.getString(i);
            }
            entryArray[results.getPosition()] = new HolderTimetableEntry(tempStrArrayHolder, true);
        }

        // Close results cursor.
        results.close();

        Log.d(Tag.LOG, "Successfully executed database query. Number of entries = " + String.valueOf(entryArray.length));

        return entryArray;
    }
}
