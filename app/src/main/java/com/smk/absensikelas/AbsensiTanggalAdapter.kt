package com.smk.absensikelas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AbsensiTanggalAdapter(
    private val list: List<Map<String, String>>,
    private val onEdit: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<AbsensiTanggalAdapter.VH>() {

    interface OnActionClick {
        fun onEdit(tanggal: String)
        fun onDelete(tanggal: String)
    }

    constructor(list: List<Map<String, String>>, listener: OnActionClick) : this(
        list,
        { listener.onEdit(it) },
        { listener.onDelete(it) }
    )

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTanggal: TextView = v.findViewById(R.id.tvTanggalItem)
        val tvStatus: TextView = v.findViewById(R.id.tvStatusItem)
        val btnEdit: ImageButton = v.findViewById(R.id.btnEditAbsensi)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteAbsensi)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tanggal_absensi, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val map = list[position]
        val tanggal = map["tanggal"] ?: ""
        
        val hdk = map["Hadir"] ?: "0"
        val skt = map["Sakit"] ?: "0"
        val izn = map["Izin"] ?: "0"
        val alp = map["Alpha"] ?: "0"
        val bls = map["Bolos"] ?: "0"
        
        val info = "H: $hdk | S: $skt | I: $izn | A: $alp | B: $bls"
        
        h.tvTanggal.text = tanggal
        h.tvStatus.text = info

        h.btnEdit.setOnClickListener { onEdit(tanggal) }
        h.btnDelete.setOnClickListener { onDelete(tanggal) }
    }

    override fun getItemCount() = list.size
}
