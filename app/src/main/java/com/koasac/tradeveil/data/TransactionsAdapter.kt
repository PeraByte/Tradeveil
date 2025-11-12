package com.koasac.tradeveil

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TransactionsAdapter(
    private var items: List<TransactionItem>,
    private val onItemClick: (Transfer) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    fun updateItems(newItems: List<TransactionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TransactionItem.SentTransfer -> VIEW_TYPE_SENT
            is TransactionItem.ReceivedTransfer -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_sent_transfer, parent, false)
                SentTransferViewHolder(view, onItemClick)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_received_transfer, parent, false)
                ReceivedTransferViewHolder(view, onItemClick)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentTransferViewHolder -> holder.bind((items[position] as TransactionItem.SentTransfer).transfer)
            is ReceivedTransferViewHolder -> holder.bind((items[position] as TransactionItem.ReceivedTransfer).transfer)
        }
    }

    override fun getItemCount(): Int = items.size

    class SentTransferViewHolder(
        itemView: View,
        private val onItemClick: (Transfer) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(transfer: Transfer) {
            itemView.findViewById<TextView>(R.id.receiverEmail).text = transfer.receiverEmail
            itemView.findViewById<TextView>(R.id.pointsAmount).text = transfer.getFormattedPoints()
            itemView.findViewById<TextView>(R.id.transferDate).text = transfer.getFormattedDate()

            itemView.setOnClickListener { onItemClick(transfer) }
        }
    }

    class ReceivedTransferViewHolder(
        itemView: View,
        private val onItemClick: (Transfer) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(transfer: Transfer) {
            itemView.findViewById<TextView>(R.id.senderEmail).text = transfer.senderEmail
            itemView.findViewById<TextView>(R.id.pointsAmount).text = transfer.getFormattedPoints()
            itemView.findViewById<TextView>(R.id.transferDate).text = transfer.getFormattedDate()

            itemView.setOnClickListener { onItemClick(transfer) }
        }
    }
}