package np.com.bimalkafle.mybackgroundapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import np.com.bimalkafle.mybackgroundapp.R

class DrawerAdapter(
    private val items: List<DrawerItem>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<DrawerAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val title: TextView = view.findViewById(R.id.title)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.drawer_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
        holder.itemView.isEnabled = item.enabled
        holder.title.setTextColor(
            if (item.enabled) Color.parseColor("#3A6BFF") else Color.parseColor("#B0B8C1")
        )
        holder.subtitle.setTextColor(Color.parseColor("#B0B8C1"))
        holder.itemView.alpha = if (item.enabled) 1f else 0.5f
        holder.itemView.setOnClickListener {
            if (item.enabled) onClick(position)
        }
    }
} 