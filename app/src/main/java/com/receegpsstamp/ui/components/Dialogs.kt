package com.receegpsstamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.receegpsstamp.data.model.Company
import com.receegpsstamp.ui.theme.NeutralSurface
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.StatusError

@Composable
fun AddItemDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(NeutralSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeutralText)
            CompactTextField(label = label, value = value, onValueChange = { value = it }, placeholder = placeholder)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(label = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Add", onClick = {
                    if (value.isNotBlank()) onSave(value.trim())
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun EditDistributorDialog(
    distributor: com.receegpsstamp.data.model.Distributor,
    companies: List<Company>,
    onDismiss: () -> Unit,
    onSave: (com.receegpsstamp.data.model.Distributor) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(distributor.name) }
    var city by remember { mutableStateOf(distributor.city) }
    var contact by remember { mutableStateOf(distributor.contact) }
    var waGroup by remember { mutableStateOf(distributor.waGroup) }
    var selectedCompany by remember { mutableStateOf(companies.find { it.id == distributor.companyId }) }
    var companyDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(NeutralSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Distributor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeutralText, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusError, modifier = Modifier.clickable { onDelete() }.padding(4.dp))
                }
            }

            Text("Company", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
            Box {
                CompactDropdown(
                    value = selectedCompany?.name ?: "Select company…",
                    onClick = { companyDropdown = true },
                )
                DropdownMenu(
                    expanded = companyDropdown,
                    onDismissRequest = { companyDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.7f).background(NeutralSurface),
                ) {
                    companies.forEach { co ->
                        DropdownMenuItem(
                            text = { Text(co.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            onClick = { selectedCompany = co; companyDropdown = false },
                        )
                    }
                }
            }

            CompactTextField(label = "Distributor name", value = name, onValueChange = { name = it }, placeholder = "Distributor name")
            CompactTextField(label = "City", value = city, onValueChange = { city = it }, placeholder = "City")
            CompactTextField(label = "Contact", value = contact, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) contact = v }, placeholder = "10-digit mobile no.", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            CompactTextField(label = "WhatsApp group (optional)", value = waGroup, onValueChange = { waGroup = it }, placeholder = "Group name to share recces to")
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(label = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = {
                    if (name.isNotBlank() && selectedCompany != null) {
                        onSave(distributor.copy(
                            name = name.trim(), city = city.trim(), contact = contact.trim(),
                            waGroup = waGroup.trim(),
                            companyId = selectedCompany!!.id, companyName = selectedCompany!!.name,
                        ))
                        onDismiss()
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AddDistributorDialog(
    onDismiss: () -> Unit,
    companies: List<Company>,
    preselectCompany: Company? = null,
    onSave: (name: String, city: String, companyId: String, companyName: String, contact: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    // Pre-fill the company already selected on the Project screen so the user doesn't pick it again.
    var selectedCompany by remember { mutableStateOf(preselectCompany) }
    var companyDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(NeutralSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Add Distributor", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeutralText)

            Text("Company", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeutralText)
            Box {
                CompactDropdown(
                    value = selectedCompany?.name ?: "Select company…",
                    onClick = { companyDropdown = true },
                )
                DropdownMenu(
                    expanded = companyDropdown,
                    onDismissRequest = { companyDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.7f).background(NeutralSurface),
                ) {
                    companies.forEach { co ->
                        DropdownMenuItem(
                            text = { Text(co.name, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            onClick = { selectedCompany = co; companyDropdown = false },
                        )
                    }
                    if (companies.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No companies — add one first", fontSize = 13.sp, color = NeutralText.copy(0.5f)) },
                            onClick = { companyDropdown = false },
                        )
                    }
                }
            }

            CompactTextField(label = "Distributor name", value = name, onValueChange = { name = it }, placeholder = "Distributor name")
            CompactTextField(label = "City", value = city, onValueChange = { city = it }, placeholder = "City")
            CompactTextField(label = "Contact", value = contact, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 10) contact = v }, placeholder = "10-digit mobile no.", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(label = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = {
                    if (name.isNotBlank() && selectedCompany != null) {
                        onSave(name.trim(), city.trim(), selectedCompany!!.id, selectedCompany!!.name, contact.trim())
                        onDismiss()
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

