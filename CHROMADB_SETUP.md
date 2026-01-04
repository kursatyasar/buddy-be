# ChromaDB RAG Integration Guide

## Overview

Bu proje ChromaDB vektör veritabanı kullanarak RAG (Retrieval-Augmented Generation) işlemi yapmaktadır. Kullanıcı sorularına cevap verirken, ChromaDB'den ilgili bilgileri çekerek daha doğru ve bağlamsal cevaplar üretir.

## Docker ile ChromaDB Kurulumu

### 1. ChromaDB'yi Başlatma

```bash
# Tüm servisleri (PostgreSQL, ChromaDB, Adminer) başlat
docker-compose up -d

# Sadece ChromaDB'yi başlat
docker-compose up -d chromadb
```

### 2. ChromaDB Erişim Bilgileri

- **URL**: http://localhost:8000
- **API Endpoint**: http://localhost:8000/api/v1
- **Health Check**: http://localhost:8000/api/v1/heartbeat

### 3. ChromaDB Durumunu Kontrol Etme

```bash
# ChromaDB container durumunu kontrol et
docker ps | grep chromadb

# ChromaDB loglarını görüntüle
docker logs buddy-chromadb

# ChromaDB'yi durdur
docker-compose stop chromadb

# ChromaDB'yi kaldır (veriler kalır)
docker-compose down chromadb
```

## API Kullanımı

### 1. Bilgi Tabanına Doküman Ekleme

```bash
curl -X POST http://localhost:8080/api/v1/rag/documents \
  -H "Content-Type: application/json" \
  -d '{
    "texts": [
      "Vodafone Türkiye'nin en büyük telekomünikasyon şirketlerinden biridir.",
      "Vodafone müşterilerine 5G, fiber internet ve dijital hizmetler sunmaktadır.",
      "Vodafone'un müşteri hizmetleri 7/24 hizmet vermektedir."
    ],
    "ids": ["doc1", "doc2", "doc3"],
    "metadata": {
      "source": "internal-wiki",
      "category": "company-info"
    }
  }'
```

### 2. Benzer Dokümanları Arama

```bash
curl "http://localhost:8080/api/v1/rag/search?query=Vodafone%20hizmetleri&topK=3"
```

### 3. Chat ile RAG Kullanımı

RAG otomatik olarak chat mesajlarında kullanılır. Kullanıcı bir soru sorduğunda:

1. Soru ChromaDB'de aranır
2. İlgili dokümanlar bulunur
3. Bu dokümanlar AI'a context olarak verilir
4. AI daha doğru ve bağlamsal cevap üretir

**Örnek Chat İsteği:**
```bash
curl -X POST http://localhost:8080/api/v1/chat/send \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "session-123",
    "content": "Vodafone'un hizmetleri nelerdir?",
    "userId": "user-456"
  }'
```

## Yapılandırma

`application.yml` dosyasında ChromaDB ayarları:

```yaml
spring:
  chromadb:
    base-url: http://localhost:8000
    collection-name: buddy-knowledge-base
    embedding-dimension: 384
    top-k: 5
```

### Environment Variables

```bash
export CHROMADB_BASE_URL=http://localhost:8000
export CHROMADB_COLLECTION=buddy-knowledge-base
export CHROMADB_EMBEDDING_DIMENSION=384
export CHROMADB_TOP_K=5
```

## Embedding Service

Şu anda basit bir hash-based embedding kullanılmaktadır. Production ortamında:

1. **Dedicated Embedding Model**: OpenAI, Cohere, veya Hugging Face embedding modelleri
2. **Embedding API**: Özel embedding servisi
3. **Local Embedding Model**: Sentence Transformers gibi local modeller

Embedding service'i güncellemek için `EmbeddingService.java` dosyasını düzenleyin.

## Veri Yönetimi

### ChromaDB Verilerini Görüntüleme

ChromaDB REST API'sini kullanarak:

```bash
# Collection'ları listele
curl http://localhost:8000/api/v1/collections

# Collection detaylarını görüntüle
curl http://localhost:8000/api/v1/collections/buddy-knowledge-base
```

### Veri Yedekleme

ChromaDB verileri Docker volume'de saklanır:

```bash
# Volume'ü yedekle
docker run --rm -v buddy-service_chromadb_data:/data -v $(pwd):/backup \
  alpine tar czf /backup/chromadb-backup.tar.gz /data

# Volume'ü geri yükle
docker run --rm -v buddy-service_chromadb_data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/chromadb-backup.tar.gz -C /
```

## Troubleshooting

### ChromaDB Başlamıyor

```bash
# Logları kontrol et
docker logs buddy-chromadb

# Container'ı yeniden başlat
docker-compose restart chromadb
```

### Bağlantı Hatası

- ChromaDB'nin çalıştığını kontrol edin: `curl http://localhost:8000/api/v1/heartbeat`
- Port 8000'in kullanılabilir olduğunu kontrol edin
- Firewall ayarlarını kontrol edin

### Embedding Boyut Uyumsuzluğu

Collection'ı silip yeniden oluşturun veya `embedding-dimension` ayarını kontrol edin.

## Production Notları

1. **Embedding Model**: Production'da gerçek bir embedding modeli kullanın
2. **ChromaDB Authentication**: Production'da authentication ekleyin
3. **Backup Strategy**: Düzenli yedekleme stratejisi oluşturun
4. **Monitoring**: ChromaDB performansını izleyin
5. **Scaling**: Gerekirse ChromaDB'yi cluster modunda çalıştırın

