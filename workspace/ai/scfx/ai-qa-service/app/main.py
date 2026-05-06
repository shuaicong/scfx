from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.knowledge import router as knowledge_router
from app.api.chat import router as chat_router

app = FastAPI(title="AI QA Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(knowledge_router)
app.include_router(chat_router)

@app.get("/health")
async def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5002)
