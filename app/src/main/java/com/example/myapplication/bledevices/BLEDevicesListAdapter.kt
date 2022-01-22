package com.example.myapplication.bledevices

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.myapplication.R

import android.widget.TextView


class BLEDevicesListAdapter(context: Context) :
    RecyclerView.Adapter<BLEDevicesListAdapter.ViewHolder>() {
    private var mData: MutableList<BluetoothDevice> = ArrayList()
    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    var clickListener: ItemClickListener? = null


    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        var myTextView: TextView? = null

        init {
            myTextView = itemView.findViewById(R.id.device_name_text_view)
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

    private fun deviceToString(device: BluetoothDevice): String {
        val devName = if (device.name != null) device.name else "unknown"
        val connectionState = when(device.bondState) {
            BOND_NONE -> "Not connected"
            BOND_BONDING -> "Connecting"
            BOND_BONDED -> "Connected"
            else -> "WTF State"
        }
        return "$devName @ ${device.address} ($connectionState)"
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = mData[position]
        holder.myTextView?.text = deviceToString(dev)
    }

    override fun getItemCount(): Int {
        return mData.size
    }

    fun getItem(position: Int): String {
        return deviceToString(mData[position])
    }

    fun addDevice(device: BluetoothDevice) {
        if (device.name == null) return

        if (!mData.contains(device)) {
            mData.add(device)
            notifyItemInserted(mData.size - 1)
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