package com.smk.absensikelas

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KelasAdapter(
    private var listKelas: List<Map<String, String>>,
    private val onItemClick: (Int, String) -> Unit,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int, String) -> Unit
) : RecyclerView.Adapter<KelasAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNama: TextView = view.findViewById(R.id.tvNamaKelasCard)
        val tvJurusan: TextView = view.findViewById(R.id.tvJurusanCard)
        val tvWali: TextView = view.findViewById(R.id.tvWaliCard)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditKelas)
        val btnHapus: ImageButton = view.findViewById(R.id.btnHapusKelas)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_kelas, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = listKelas.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val kelas = listKelas[position]
        val id = kelas["id"]?.toIntOrNull()?: 0
        val namaDb = kelas["nama_kelas"]?: ""

        // ambil tahun aktif
        val context = holder.itemView.context
        val tahunFull = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("tahun_ajaran", "2025/2026 - Ganjil")?: "2025/2026 - Ganjil"

        val namaBersih = namaDb.replace(Regex("\\s*-\\s*\\d{4}/\\d{4}.*"), "").trim()
        val namaTampil = "$namaBersih - $tahunFull"

        holder.tvNama.text = namaTampil
        holder.tvJurusan.text = "Jurusan: ${kelas["jurusan"]}"
        holder.tvWali.text = "Wali: ${kelas["wali_kelas"]}"

        holder.itemView.setOnClickListener { onItemClick(id, namaTampil) }
        holder.btnEdit.setOnClickListener { onEditClick(id) }
        holder.btnHapus.setOnClickListener { onDeleteClick(id, namaTampil) }
    }

    fun updateData(newList: List<Map<String, String>>) {
        listKelas = newList
        notifyDataSetChanged()
    }
}