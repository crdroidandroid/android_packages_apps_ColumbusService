/*
 * SPDX-FileCopyrightText: The Dirty Unicorns Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions.apppicker

import android.app.ListActivity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import java.util.ArrayList
import java.util.Arrays
import org.protonaosp.columbus.R

open class AppPicker : ListActivity() {

    lateinit var pm: PackageManager
    var applist: MutableList<ApplicationInfo>? = null
    var adapter: Adapter? = null

    var mActivitiesList: MutableList<ActivityInfo>? = null
    var mIsActivitiesList: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.R.layout.list_content)
        title = getString(R.string.app_select_title)

        pm = getPackageManager()
        LoadApplications(this).execute()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (!mIsActivitiesList) {
            // we are in the Apps list
        } else if (mIsActivitiesList) {
            // we are in the Activities list
        }

        mIsActivitiesList = false
        finish()
    }

    override fun onBackPressed() {
        if (mIsActivitiesList) {
            listAdapter = adapter
            title = getString(R.string.app_select_title)
            // Reset the dialog again
            mIsActivitiesList = false
        } else {
            finish()
        }
    }

    fun getStringArrayResSafely(resId: Int): Array<String> {
        var strArr = resources.getStringArray(resId)
        if (strArr == null) strArr = arrayOf<String>()
        return strArr
    }

    fun checkForLaunchIntent(list: MutableList<ApplicationInfo>?): MutableList<ApplicationInfo>? {
        val applist = mutableListOf<ApplicationInfo>()

        // If we need to blacklist apps, this is where we list them
        val blacklistPackages = getStringArrayResSafely(R.array.app_picker_block_list)

        if (list == null) return applist

        for (info in list) {
            try {
                /* Remove blacklisted apps from the list of apps we give to
                the user to select from. */
                if (
                    !blacklistPackages.contains(info.packageName) &&
                        pm.getLaunchIntentForPackage(info.packageName) != null
                ) {
                    applist.add(info)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Lets alphabatize the list of installed user apps
        applist.sortWith(ApplicationInfo.DisplayNameComparator(pm))

        return applist
    }

    class LoadApplications(val activity: AppPicker) : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void?): Void? {
            activity.applist =
                activity.checkForLaunchIntent(
                    activity.pm.getInstalledApplications(PackageManager.GET_META_DATA)
                )
            activity.adapter =
                Adapter(activity, R.layout.app_list_item, activity.applist!!, activity.pm)
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            activity.listAdapter = activity.adapter

            activity.listView.isLongClickable = true
            activity.listView.onItemLongClickListener =
                AdapterView.OnItemLongClickListener { arg0, arg1, pos, id ->
                    activity.onLongClick(pos)
                    true
                }
        }

        override fun onPreExecute() {
            super.onPreExecute()
        }
    }

    open fun onLongClick(pos: Int) {
        /*if (mIsActivitiesList) return;
        String packageName = applist.get(position).packageName;
        showActivitiesDialog(packageName);*/
    }

    fun showActivitiesDialog(packageName: String) {
        mIsActivitiesList = true
        var list: ArrayList<ActivityInfo>? = null
        try {
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            list = ArrayList(Arrays.asList(*pi.activities))
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle exception
        }

        mActivitiesList = list

        if (list == null) {
            // no activities to show, let's stay in the Apps list
            mIsActivitiesList = false
            return
        }

        title = getString(R.string.activity_select_title)
        // switch to a new adapter to show app activities
        val adapter = ActivitiesAdapter(this, R.layout.app_list_item, list, pm)
        listAdapter = adapter
    }

    class Adapter(
        context: Context,
        resource: Int,
        objects: List<ApplicationInfo>,
        private val _pm: PackageManager,
    ) : ArrayAdapter<ApplicationInfo>(context, resource, objects) {

        private val appList: List<ApplicationInfo>
        private val context: Context

        init {
            this.context = context
            this.appList = objects
        }

        override fun getCount(): Int = appList?.size ?: 0

        override fun getItem(position: Int): ApplicationInfo? = appList?.get(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            val info = appList[position]

            if (view == null) {
                val layoutInflater =
                    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                view = layoutInflater.inflate(R.layout.app_list_item, null)
            }

            if (info != null) {
                val appName = view!!.findViewById<TextView>(R.id.app_name)
                val iconView = view.findViewById<ImageView>(R.id.app_icon)

                appName.text = info.loadLabel(_pm)
                iconView.setImageDrawable(info.loadIcon(_pm))
            }
            return view!!
        }
    }

    class ActivitiesAdapter(
        context: Context,
        resource: Int,
        objects: List<ActivityInfo>,
        private val _pm: PackageManager,
    ) : ArrayAdapter<ActivityInfo>(context, resource, objects) {

        private val appList: List<ActivityInfo>
        private val context: Context

        init {
            this.context = context
            this.appList = objects
        }

        override fun getCount(): Int = appList?.size ?: 0

        override fun getItem(position: Int): ActivityInfo? = appList?.get(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            val info = appList[position]

            if (view == null) {
                val layoutInflater =
                    context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
            }

            if (info != null) {
                val appName = view!!.findViewById<TextView>(android.R.id.text1)

                val name = info.name
                appName.text = name
            }

            return view!!
        }
    }
}
