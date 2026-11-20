import QRCode from "qrcode";
import { esc, printHtml, upiLink } from "../lib/erp";
import { dateTime, rupees } from "../lib/format";
import { rpc } from "../lib/erp";

interface ReceiptData {
  orderNumber: string; orderType: string; tableNumber: string | null; customerName: string; createdAt: string;
  items: { nameSnapshot: string; quantity: number; unitPricePaise: number; lineTotalPaise: number; options: { name: string }[] }[];
  subtotalPaise: number; discountPaise: number; taxPaise: number; taxIncludedPaise: number; taxRateBps: number;
  packagingFeePaise: number; deliveryFeePaise: number; totalPaise: number; printNumber: number;
  payments: { tender: string; amountPaise: number; reference: string | null }[];
  business: { name: string; tradeName: string; address: string; phone: string; gstin: string | null; fssai: string | null;
    gstRegistered: boolean; gstScheme: string; sacCode: string; upiId: string | null; footer: string | null; widthMm: number };
}

/** Prints a thermal receipt / GST tax invoice (58 or 80 mm) in the browser — works with any printer driver. */
export async function printReceipt(orderId: string, kot = false) {
  const r = await rpc<ReceiptData>("get_receipt", { p_order_id: orderId, p_count_print: !kot });
  const b = r.business;
  const paid = r.payments.reduce((s, p) => s + p.amountPaise, 0);
  const due = r.totalPaise - paid;
  const tax = r.taxPaise + r.taxIncludedPaise;
  const docTitle = kot ? "KOT — KITCHEN ORDER" : b.gstRegistered && b.gstScheme === "REGULAR" ? "TAX INVOICE" : "BILL OF SUPPLY";
  let qr = "";
  if (!kot && due > 0 && b.upiId) {
    qr = `<div class="c"><img src="${await QRCode.toDataURL(upiLink(b.upiId, b.tradeName, due, r.orderNumber), { width: 180, margin: 1 })}" /><br/>Scan to pay ${rupees(due)} by UPI</div>`;
  }
  const lines = r.items.map((i) => `<tr><td>${esc(i.nameSnapshot)}${i.options.length ? `<br/><small>${esc(i.options.map((o) => o.name).join(", "))}</small>` : ""}</td>
    <td class="r">${i.quantity}</td>${kot ? "" : `<td class="r">${rupees(i.lineTotalPaise)}</td>`}</tr>`).join("");
  const html = `
    <h2>${esc(b.tradeName)}</h2>
    ${kot ? "" : `<div class="c">${esc(b.name !== b.tradeName ? b.name + " · " : "")}${esc(b.address)}<br/>Ph ${esc(b.phone)}
      ${b.gstin ? `<br/>GSTIN ${esc(b.gstin)}` : ""}${b.fssai ? `<br/>FSSAI ${esc(b.fssai)}` : ""}</div>`}
    <hr/><div class="c big">${docTitle}</div>
    ${r.printNumber > 1 && !kot ? `<div class="c">*** DUPLICATE COPY (${r.printNumber}) ***</div>` : ""}
    <div>No: ${esc(r.orderNumber)}<br/>${esc(dateTime(r.createdAt))} · ${esc(r.orderType)}${r.tableNumber ? " · Table " + esc(r.tableNumber) : ""}
      ${r.customerName && r.customerName !== "Walk-in" ? "<br/>Customer: " + esc(r.customerName) : ""}</div>
    <hr/><table><tr><th style="text-align:left">Item</th><th class="r">Qty</th>${kot ? "" : '<th class="r">Amt</th>'}</tr>${lines}</table><hr/>
    ${kot ? "" : `<table>
      <tr><td>Subtotal</td><td class="r">${rupees(r.subtotalPaise)}</td></tr>
      ${r.discountPaise ? `<tr><td>Discount</td><td class="r">−${rupees(r.discountPaise)}</td></tr>` : ""}
      ${r.packagingFeePaise + r.deliveryFeePaise ? `<tr><td>Packaging/Delivery</td><td class="r">${rupees(r.packagingFeePaise + r.deliveryFeePaise)}</td></tr>` : ""}
      ${b.gstRegistered && tax > 0 ? `<tr><td>CGST ${(r.taxRateBps / 200).toFixed(1)}%${r.taxIncludedPaise ? " (incl.)" : ""}</td><td class="r">${rupees(Math.floor(tax / 2))}</td></tr>
        <tr><td>SGST ${(r.taxRateBps / 200).toFixed(1)}%${r.taxIncludedPaise ? " (incl.)" : ""}</td><td class="r">${rupees(tax - Math.floor(tax / 2))}</td></tr>
        <tr><td colspan="2"><small>SAC ${esc(b.sacCode)}</small></td></tr>` : ""}
      <tr class="big"><td>TOTAL</td><td class="r">${rupees(r.totalPaise)}</td></tr>
      ${r.payments.map((p) => `<tr><td>Paid ${esc(p.tender)}${p.reference ? " " + esc(p.reference) : ""}</td><td class="r">${rupees(p.amountPaise)}</td></tr>`).join("")}
      ${due > 0 ? `<tr class="big"><td>BALANCE DUE</td><td class="r">${rupees(due)}</td></tr>` : ""}
    </table>${qr}<hr/><div class="c">${esc(b.footer ?? "Thank you! Visit again.")}</div>`}`;
  printHtml(r.orderNumber, html, b.widthMm);
}
