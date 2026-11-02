import "./globals.css";

export const metadata = {
  title: "Sportiva | Quản lý cửa hàng",
  description: "Hệ thống quản lý cửa hàng thể thao",
};

export default function RootLayout({ children }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
