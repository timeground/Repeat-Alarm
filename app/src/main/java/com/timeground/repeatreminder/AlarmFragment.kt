package com.timeground.repeatreminder

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.timeground.repeatreminder.data.Alarm
import com.timeground.repeatreminder.data.AlarmDatabaseHelper
import com.timeground.repeatreminder.utils.AlarmScheduler

class AlarmFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAdd: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var tvEmptyState: TextView
    private lateinit var dbHelper: AlarmDatabaseHelper
    private lateinit var adapter: AlarmAdapter
    private var alarmList = mutableListOf<Alarm>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_alarm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dbHelper = AlarmDatabaseHelper(requireContext())
        
        recyclerView = view.findViewById(R.id.recyclerView)
        fabAdd = view.findViewById(R.id.fabAdd)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = AlarmAdapter(alarmList, 
            onDelete = { alarm -> 
                 deleteAlarm(alarm)
            },
            onToggle = { alarm, isEnabled ->
                toggleAlarm(alarm, isEnabled)
            },
            onClick = { alarm ->
                showAddAlarmDialog(alarm)
            }
        )
        recyclerView.adapter = adapter
        
        fabAdd.setOnClickListener { showAddAlarmDialog() }
        
        loadAlarms()
    }

    private fun loadAlarms() {
        alarmList.clear()
        alarmList.addAll(dbHelper.getAllAlarms())
        adapter.notifyDataSetChanged()
        
        if (alarmList.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
        } else {
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun showAddAlarmDialog(alarmToEdit: Alarm? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_alarm, null)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.timePicker)
        val cbSun = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSun)
        val cbMon = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMon)
        val cbTue = dialogView.findViewById<android.widget.CheckBox>(R.id.cbTue)
        val cbWed = dialogView.findViewById<android.widget.CheckBox>(R.id.cbWed)
        val cbThu = dialogView.findViewById<android.widget.CheckBox>(R.id.cbThu)
        val cbFri = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFri)
        val cbSat = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSat)
        
        val checkBoxes = listOf(cbSun, cbMon, cbTue, cbWed, cbThu, cbFri, cbSat)
        
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(requireContext()))

        if (alarmToEdit != null) {
            // Edit Mode: Pre-fill data
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                timePicker.hour = alarmToEdit.hour
                timePicker.minute = alarmToEdit.minute
            } else {
                timePicker.currentHour = alarmToEdit.hour
                timePicker.currentMinute = alarmToEdit.minute
            }
            
            val indices = alarmToEdit.days.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            if (indices.isEmpty() && alarmToEdit.days.isEmpty()) {
                // One-off: Indices empty. Keep checks false? Or undefined behavior?
                // Logic: show unchecked if one-off.
                checkBoxes.forEach { it.isChecked = false }
            } else {
                checkBoxes.forEachIndexed { index, checkBox ->
                    checkBox.isChecked = indices.contains(index)
                }
            }
        } else {
             // Add Mode: Default to current time and all days
            val calendar = java.util.Calendar.getInstance()
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                timePicker.hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                timePicker.minute = calendar.get(java.util.Calendar.MINUTE)
            } else {
                timePicker.currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                timePicker.currentMinute = calendar.get(java.util.Calendar.MINUTE)
            }
            checkBoxes.forEach { it.isChecked = true }
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(if (alarmToEdit == null) "Add Alarm" else "Edit Alarm")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val hour = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) timePicker.hour else timePicker.currentHour
                val minute = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) timePicker.minute else timePicker.currentMinute
                
                // Build days string
                val selectedIndices = mutableListOf<Int>()
                checkBoxes.forEachIndexed { index, checkBox ->
                    if (checkBox.isChecked) selectedIndices.add(index)
                }
                val daysString = selectedIndices.joinToString(",")
                
                if (alarmToEdit != null) {
                    // Update existing
                    alarmToEdit.hour = hour
                    alarmToEdit.minute = minute
                    alarmToEdit.days = daysString
                    alarmToEdit.isEnabled = true // Re-enable on edit usually
                    dbHelper.updateAlarm(alarmToEdit)
                    AlarmScheduler.scheduleAlarm(requireContext(), alarmToEdit)
                } else {
                    // Create new
                    val newAlarm = Alarm(hour = hour, minute = minute, isEnabled = true, days = daysString)
                    val id = dbHelper.addAlarm(newAlarm)
                    val savedAlarm = dbHelper.getAlarm(id)
                    if (savedAlarm != null) {
                        AlarmScheduler.scheduleAlarm(requireContext(), savedAlarm)
                    }
                }
                loadAlarms()
            }
            .setNegativeButton("Cancel", null)

        if (alarmToEdit != null) {
            builder.setNeutralButton("Delete") { _, _ ->
                deleteAlarm(alarmToEdit)
            }
        }

        builder.show()
    }
    
    private fun deleteAlarm(alarm: Alarm) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Alarm")
            .setMessage("Are you sure you want to delete this alarm?")
            .setPositiveButton("Delete") { _, _ ->
                AlarmScheduler.cancelAlarm(requireContext(), alarm)
                dbHelper.deleteAlarm(alarm.id)
                loadAlarms()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        alarm.isEnabled = isEnabled
        dbHelper.updateAlarm(alarm)
        if (isEnabled) {
            AlarmScheduler.scheduleAlarm(requireContext(), alarm)
            Toast.makeText(context, "Alarm Scheduled", Toast.LENGTH_SHORT).show()
        } else {
            AlarmScheduler.cancelAlarm(requireContext(), alarm)
        }
    }
    
    // Inner Adapter Class
    inner class AlarmAdapter(
        private val alarms: List<Alarm>,
        private val onDelete: (Alarm) -> Unit,
        private val onToggle: (Alarm, Boolean) -> Unit,
        private val onClick: (Alarm) -> Unit
    ) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

        inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
            val tvDays: TextView = itemView.findViewById(R.id.tvDays)
            val switchEnable: androidx.appcompat.widget.SwitchCompat = itemView.findViewById(R.id.switchEnable)
            val cardView: androidx.cardview.widget.CardView = itemView as androidx.cardview.widget.CardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
            return AlarmViewHolder(view)
        }

        override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
            val alarm = alarms[position]
            holder.tvTime.text = alarm.getFormattedTime()
            holder.tvLabel.text = if (alarm.label.isEmpty()) "Alarm" else alarm.label
            holder.tvDays.text = formatDays(alarm.days)
            
            // Set click listener on card/item
            holder.itemView.setOnClickListener {
                onClick(alarm)
            }
            holder.switchEnable.setOnCheckedChangeListener(null)
            holder.switchEnable.isChecked = alarm.isEnabled
            
            holder.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }
            
            holder.itemView.setOnLongClickListener {
                onDelete(alarm)
                true
            }
        }
        
        private fun formatDays(daysStr: String): String {
             if (daysStr.isEmpty()) return "Once"
             val indices = daysStr.split(",").mapNotNull { it.toIntOrNull() }.sorted()
             if (indices.size == 7) return "Daily"
             if (indices.isEmpty()) return "Once"
             
             // 0=Sun, 1=Mon...
             val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
             return indices.joinToString(", ") { dayNames.getOrElse(it) { "" } }
        }

        override fun getItemCount() = alarms.size
    }
}
