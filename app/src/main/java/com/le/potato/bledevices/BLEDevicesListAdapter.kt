package com.le.potato.bledevices

import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import com.le.potato.R

import android.widget.TextView


class BLEDevicesListAdapter(context: Context) :
    RecyclerView.Adapter<BLEDevicesListAdapter.ViewHolder>() {
    private var mData: MutableList<BTDeviceWrapper> = ArrayList()
    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    var clickListener: ItemClickListener? = null


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        var deviceName: TextView? = null
        var connectionProgressBar: ProgressBar? = null
        var statusIcon: ImageView? = null

        init {
            deviceName = itemView.findViewById(R.id.device_name_text)
            connectionProgressBar = itemView.findViewById(R.id.connection_spinner)
            statusIcon = itemView.findViewById(R.id.status_icon)
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            clickListener?.onItemClick(view, adapterPosition)
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = mInflater.inflate(R.layout.ble_device_row, parent, false)
        return ViewHolder(view)
    }

    private fun deviceToString(device: BTDeviceWrapper): String? {
         return device.name ?: device.address
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = mData[position]
        holder.deviceName?.text = deviceToString(dev)
        Log.i("onBindViewHolder", "Dev ${dev.name} state = ${dev.connectionState}")
        when (dev.connectionState) {
            BluetoothProfile.STATE_CONNECTING -> {
                holder.connectionProgressBar?.visibility = View.VISIBLE
                holder.statusIcon?.visibility = View.INVISIBLE
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                holder.connectionProgressBar?.visibility = View.INVISIBLE
                holder.statusIcon?.visibility = View.VISIBLE
                holder.statusIcon?.setImageResource(R.drawable.ic_plug_in)
            }
            else -> {
                holder.connectionProgressBar?.visibility = View.INVISIBLE
                holder.statusIcon?.visibility = View.VISIBLE
                holder.statusIcon?.setImageResource(R.drawable.ic_baseline_done_24)
            }

        }
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    fun getItem(position: Int): BTDeviceWrapper {
        return mData[position]
    }

    fun addDevice(device: BTDeviceWrapper) {
        if (device.name == null) return

        if (!mData.contains(device)) {
            mData.add(device)
            notifyItemInserted(mData.size - 1)
        }
    }

    fun updateDevice(device: BTDeviceWrapper) {
        val index = mData.indexOfFirst { it.address == device.address }
        if (index >= 0) {
            mData[index] = device
            notifyItemChanged(index)
        }
    }

    fun clear() {
        val size = mData.size
        if (size > 0) {
            mData.clear()
            notifyItemRangeRemoved(0, size)
        }
    }

    interface ItemClickListener {
        fun onItemClick(view: View?, position: Int)
    }
}