import socket
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox

UDP_PORT = 8888
MESSAGE = b"BATTERY_QUERY"
TIMEOUT = 0.05 # Velmi krátký timeout pro každou IP, aby sken netrval věčnost

class BatteryMonitorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Battery Monitor (Deep Scan)")
        self.root.geometry("650x400")

        style = ttk.Style()
        style.theme_use('vista')
        
        main_frame = ttk.Frame(root, padding="15")
        main_frame.pack(fill=tk.BOTH, expand=True)

        ctrl_frame = ttk.Frame(main_frame)
        ctrl_frame.pack(fill=tk.X, pady=(0, 10))

        self.btn_scan = ttk.Button(ctrl_frame, text="🔍 Hloubkový sken sítě", command=self.start_scan_thread)
        self.btn_scan.pack(side=tk.LEFT, padx=5)

        self.status_label = ttk.Label(ctrl_frame, text="Připraven", font=('Segoe UI', 9))
        self.status_label.pack(side=tk.LEFT, padx=15)

        self.tree = ttk.Treeview(main_frame, columns=('ip', 'device', 'battery', 'status'), show='headings')
        self.tree.heading('ip', text='IP Adresa')
        self.tree.heading('device', text='Zařízení')
        self.tree.heading('battery', text='Baterie')
        self.tree.heading('status', text='Stav')
        
        for col, width in zip(self.tree['columns'], [120, 220, 80, 150]):
            self.tree.column(col, width=width, anchor=tk.W if col != 'battery' else tk.CENTER)

        self.tree.tag_configure('charging', foreground='#27ae60', font=('Segoe UI', 9, 'bold'))
        self.tree.pack(fill=tk.BOTH, expand=True)

    def start_scan_thread(self):
        self.btn_scan.state(['disabled'])
        for i in self.tree.get_children(): self.tree.delete(i)
        threading.Thread(target=self.deep_scan, daemon=True).start()

    def deep_scan(self):
        found_devices = []
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(TIMEOUT)

        try:
            # Zjištění lokální sítě
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.connect(("8.8.8.8", 80))
                local_ip = s.getsockname()[0]
            
            prefix = ".".join(local_ip.split(".")[:-1])
            self.root.after(0, lambda: self.status_label.config(text=f"Skenuji síť {prefix}.X ..."))

            # Skenování všech IP adres v podsíti (1 až 254)
            for i in range(1, 255):
                target_ip = f"{prefix}.{i}"
                if target_ip == local_ip: continue # Přeskočit sebe
                
                sock.sendto(MESSAGE, (target_ip, UDP_PORT))
                
                # Zkusíme krátce počkat, jestli někdo odpoví
                try:
                    data, addr = sock.recvfrom(1024)
                    parts = data.decode('utf-8').split('|')
                    if len(parts) == 3:
                        status = "⚡ Nabíjí se" if parts[2].lower() == "true" else "🔋 Vybíjí se"
                        found_devices.append((addr[0], parts[0], f"{parts[1]}%", status, parts[2].lower() == "true"))
                except socket.timeout:
                    continue
                
        except Exception as e:
            self.root.after(0, lambda: messagebox.showerror("Chyba", str(e)))
        finally:
            sock.close()
            self.root.after(0, lambda: self.update_ui(found_devices))

    def update_ui(self, devices):
        for ip, name, batt, status, charging in devices:
            self.tree.insert('', tk.END, values=(ip, name, batt, status), tags=('charging' if charging else ''))
        self.btn_scan.state(['!disabled'])
        self.status_label.config(text=f"Hotovo. Nalezeno: {len(devices)}")

if __name__ == "__main__":
    root = tk.Tk()
    BatteryMonitorApp(root)
    root.mainloop()
