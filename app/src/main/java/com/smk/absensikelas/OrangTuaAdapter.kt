package com.smk.absensikelas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrangTuaAdapter(
    private val data: List<Map<String, String>>,
    private val onEditClick: (Map<String, String>) -> Unit,
    private val onDeleteClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<OrangTuaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus: TextView = view.findViewById(R.id.tvStatusOT)
        val tvNama: TextView = view.findViewById(R.id.tvNamaOT)
        val tvNoHp: TextView = view.findViewById(R.id.tvNoHpOT)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditOT)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteOT)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_orang_tua, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data[position]
        holder.tvStatus.text = item["status"]
        holder.tvNama.text = item["nama"]
        holder.tvNoHp.text = item["no_hp"]

        holder.btnEdit.setOnClickListener { onEditClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount() = data.size
}