package com.navassist.app.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.navassist.app.database.SensorReading
import com.navassist.app.databinding.ItemSensorReadingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the History screen.
 * Each row shows: timestamp, distance, LDR value, light level, activity.
 */
class SensorReadingAdapter :
    ListAdapter<SensorReading, SensorReadingAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSensorReadingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSensorReadingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reading: SensorReading) {
            val ts = dateFormat.format(Date(reading.timestamp))
            binding.tvTimestamp.text  = ts
            binding.tvDistance.text   = "${reading.distanceCm} cm"
            binding.tvLdrHistory.text = "${reading.ldrValue}"
            binding.tvLightHistory.text  = reading.lightLevel.replaceFirstChar { it.uppercase() }
            binding.tvActivityHistory.text = reading.activity

            // TalkBack description for the whole row
            binding.root.contentDescription =
                "Reading at $ts: distance ${reading.distanceCm} centimetres, " +
                "light ${reading.lightLevel}, activity ${reading.activity}"
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SensorReading>() {
            override fun areItemsTheSame(a: SensorReading, b: SensorReading) = a.id == b.id
            override fun areContentsTheSame(a: SensorReading, b: SensorReading) = a == b
        }
    }
}
