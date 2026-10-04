package com.smk.absensikelas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smk.absensikelas.R

class SiswaAdapter(
    private val onEditClick: (SiswaModel) -> Unit,
    private val onDeleteClick: (SiswaModel) -> Unit,
    private val onWaClick: (SiswaModel) -> Unit,
    private val enableAlert: Boolean = false,
    private val showManageButtons: Boolean = false,
    private val onItemClick: ((SiswaModel) -> Unit)? = null
) : androidx.recyclerview.widget.ListAdapter<SiswaModel, SiswaAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SiswaModel>() {
            override fun areItemsTheSame(a: SiswaModel, b: SiswaModel) = a.id == b.id
            override fun areContentsTheSame(a: SiswaModel, b: SiswaModel) = a == b
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: CardView = v.findViewById(R.id.cardSiswa)
        val tvNama: TextView = v.findViewById(R.id.tvNamaSiswa)
        val tvNis: TextView = v.findViewById(R.id.tvNisSiswa)
        val tvJk: TextView = v.findViewById(R.id.tvJenisKelamin)
        val tvAlert: TextView = v.findViewById(R.id.tvAlert)
        val btnEdit: ImageButton = v.findViewById(R.id.btnEditSiswa)
        val btnHapus: ImageButton = v.findViewById(R.id.btnHapusSiswa)
        val btnWa: ImageButton = v.findViewById(R.id.btnWaSiswa)
        val btnDetail: ImageButton = v.findViewById(R.id.btnDetailSiswa)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_siswa, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val s = getItem(position)

        h.tvNama.text = s.nama
        h.tvNis.text = "NISN : ${s.nis ?: "-"}"
        h.tvJk.text = s.jenisKelamin ?: "-"

        h.tvAlert.visibility = View.GONE
        h.btnWa.visibility = View.GONE
        h.btnEdit.visibility = View.GONE
        h.btnHapus.visibility = View.GONE
        h.btnDetail.visibility = View.VISIBLE
        h.card.setCardBackgroundColor(0xFFFFFFFF.toInt())

        if (showManageButtons) {
            h.btnEdit.visibility = View.VISIBLE
            h.btnHapus.visibility = View.VISIBLE
            h.btnDetail.visibility = View.GONE
        }

        if (enableAlert) {
            val total = s.alpha + s.bolos
            if (total > 2) {
                h.tvAlert.visibility = View.VISIBLE
                h.tvAlert.text = "⚠ ${total}x"
                h.btnWa.visibility = View.VISIBLE
                h.card.setCardBackgroundColor(0xFFFFEBEE.toInt())
            }
        }

        h.btnEdit.setOnClickListener { onEditClick(s) }
        h.btnHapus.setOnClickListener { onDeleteClick(s) }
        h.btnWa.setOnClickListener { onWaClick(s) }
        h.btnDetail.setOnClickListener { onItemClick?.invoke(s) }
        h.card.setOnClickListener { onItemClick?.invoke(s) }
    }
}