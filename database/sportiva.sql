-- MariaDB dump 10.19  Distrib 10.4.32-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: sportiva
-- ------------------------------------------------------
-- Server version	10.4.32-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `user_accounts`
--

DROP TABLE IF EXISTS `user_accounts`;
CREATE TABLE `user_accounts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(40) NOT NULL,
  `password_hash` varchar(100) NOT NULL,
  `role` varchar(10) NOT NULL,
  `enabled` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_accounts_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `categories` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(120) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'Giày thể thao','Giày chạy bộ, bóng đá và tập luyện'),(2,'Quần áo','Trang phục thể thao'),(3,'Dụng cụ','Thiết bị và phụ kiện');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customer_orders`
--

DROP TABLE IF EXISTS `customer_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `customer_orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `customer_id` bigint(20) NOT NULL,
  `order_date` datetime NOT NULL,
  `status` varchar(40) NOT NULL,
  `total_amount` decimal(14,2) NOT NULL DEFAULT 0.00,
  `shipping_address` varchar(500) DEFAULT NULL,
  `payment_method` varchar(30) DEFAULT NULL,
  `payment_status` varchar(80) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_orders_customer` (`customer_id`),
  KEY `idx_orders_date` (`order_date`),
  KEY `idx_orders_status` (`status`),
  CONSTRAINT `fk_orders_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`) ON UPDATE CASCADE,
  CONSTRAINT `chk_orders_total` CHECK (`total_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer_orders`
--

LOCK TABLES `customer_orders` WRITE;
/*!40000 ALTER TABLE `customer_orders` DISABLE KEYS */;
/*!40000 ALTER TABLE `customer_orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `customers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `full_name` varchar(150) NOT NULL,
  `phone` varchar(30) DEFAULT NULL,
  `email` varchar(160) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_customers_name` (`full_name`),
  KEY `idx_customers_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customers`
--

LOCK TABLES `customers` WRITE;
/*!40000 ALTER TABLE `customers` DISABLE KEYS */;
INSERT INTO `customers` VALUES (1,'Nguyễn Minh Anh','0901234567','minhanh@email.com','Quận 1, TP. Hồ Chí Minh'),(2,'Trần Quốc Bảo','0912345678','quocbao@email.com','Cầu Giấy, Hà Nội');
/*!40000 ALTER TABLE `customers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_details`
--

DROP TABLE IF EXISTS `order_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `order_details` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `quantity` int(11) NOT NULL,
  `unit_price` decimal(14,2) NOT NULL,
  `subtotal` decimal(14,2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_details_order` (`order_id`),
  KEY `idx_order_details_product` (`product_id`),
  CONSTRAINT `fk_order_details_order` FOREIGN KEY (`order_id`) REFERENCES `customer_orders` (`id`) ON UPDATE CASCADE,
  CONSTRAINT `fk_order_details_product` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON UPDATE CASCADE,
  CONSTRAINT `chk_order_details_quantity` CHECK (`quantity` > 0),
  CONSTRAINT `chk_order_details_unit_price` CHECK (`unit_price` >= 0),
  CONSTRAINT `chk_order_details_subtotal` CHECK (`subtotal` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_details`
--

LOCK TABLES `order_details` WRITE;
/*!40000 ALTER TABLE `order_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `order_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `products` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` bigint(20) NOT NULL,
  `name` varchar(180) NOT NULL,
  `brand` varchar(100) DEFAULT NULL,
  `size` varchar(30) DEFAULT NULL,
  `color` varchar(60) DEFAULT NULL,
  `price` decimal(14,2) NOT NULL,
  `quantity` int(11) NOT NULL,
  `status` varchar(30) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_products_category` (`category_id`),
  KEY `idx_products_name` (`name`),
  CONSTRAINT `fk_products_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`) ON UPDATE CASCADE,
  CONSTRAINT `chk_products_price` CHECK (`price` >= 0),
  CONSTRAINT `chk_products_quantity` CHECK (`quantity` >= 0)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` (`id`,`category_id`,`name`,`brand`,`size`,`color`,`price`,`quantity`,`status`,`image_url`) VALUES (1,1,'Nike Air Zoom Pegasus 40','Nike','42','Đen / Trắng',3290000.00,12,'Đang bán',NULL),(2,2,'Áo chạy bộ Dri-FIT','Nike','L','Xanh navy',890000.00,25,'Đang bán',NULL),(3,3,'Bóng đá Pro Match','SportPro','5','Trắng / Đỏ',650000.00,8,'Sắp hết',NULL),(4,1,'Adidas Ultraboost Light','Adidas','42','Black',3890000.00,9,'Đang bán',NULL),(5,1,'Puma Velocity Nitro 3','Puma','41','Blue',2790000.00,14,'Đang bán',NULL),(6,1,'Mizuno Morelia Neo IV','Mizuno','40','White',3150000.00,6,'Sắp hết',NULL),(7,1,'Jordan One Take 5 Basketball','Jordan','43','Red / Black',2490000.00,11,'Đang bán',NULL),(8,2,'Adidas Home Football Jersey 2025','Adidas','L','Red',1250000.00,20,'Đang bán',NULL),(9,2,'Nike 2-in-1 Running Shorts','Nike','M','Black',790000.00,18,'Đang bán',NULL),(10,2,'Puma Essentials Training Jacket','Puma','XL','Gray',1690000.00,7,'Sắp hết',NULL),(11,2,'Adidas Mid-Crew Sports Socks 3-Pack','Adidas','L','White',290000.00,30,'Đang bán',NULL),(12,3,'Yonex Nanoflare 001 Badminton Racket','Yonex','4U-G5','Navy',1590000.00,10,'Đang bán',NULL),(13,3,'Non-Slip Yoga Mat 6mm','SportPro','183x61cm','Purple',450000.00,16,'Đang bán',NULL),(14,3,'Nike Sport Water Bottle 750ml','Nike','750ml','Clear',390000.00,22,'Đang bán',NULL),(15,3,'Under Armour Pro Grip Gym Gloves','Under Armour','M','Black',590000.00,5,'Sắp hết',NULL);
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-01 22:05:03
