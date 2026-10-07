package com.smk.absensikelas

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class OrangTuaAdapter(
    private val data: List<Map<String, String>>,
    private val onEditClick: (Map<String, String>) -> Unit,
    private val onDeleteClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<OrangTuaAdapter.VH>() {

    class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
        val card: CardView = v.findViewById(R.id.cardOrangTua)
        val tvNamaSiswa: TextView = v.findViewById(R.id.tvOtNamaSiswa)
        val tvStatus: TextView = v.findViewById(R.id.tvOtStatus)
        val tvNama: TextView = v.findViewById(R.id.tvOtNama)
        val tvNoHp: TextView = v.findViewById(R.id.tvOtNoHp)
        val btnEdit: ImageButton = v.findViewById(R.id.btnEditOrangTua)
        val btnHapus: ImageButton = v.findViewById(R.id.btnHapusOrangTuaItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_orang_tua, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = data[position]
        val nis = d["nis"]?.takeIf { it.isNotBlank() }
        h.tvNamaSiswa.text = if (nis != null) "${d["nama_siswa"]} ($nis)" else d["nama_siswa"] ?: "-"
        h.tvStatus.text = d["status"]?.takeIf { it.isNotBlank() } ?: "Ayah"
        h.tvNama.text = d["nama"]?.takeIf { it.isNotBlank() } ?: "-"
        h.tvNoHp.text = d["no_hp"]?.takeIf { it.isNotBlank() } ?: "-"

        h.btnEdit.setOnClickListener { onEditClick(d) }
        h.btnHapus.setOnClickListener { onDeleteClick(d) }
        h.card.setOnClickListener { onEditClick(d) }
    }

    override fun getItemCount(): Int = data.size
}
