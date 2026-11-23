#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <algorithm>
#include "llama.h"

static llama_model *g_model = nullptr;
static std::mutex g_mutex;

static std::string jstr(JNIEnv *env, jstring value) {
    const char *p = env->GetStringUTFChars(value, nullptr);
    std::string out = p ? p : "";
    if (p) env->ReleaseStringUTFChars(value, p);
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codemaster_ai_MainActivity_nativeLoadModel(JNIEnv *env, jobject, jstring pathValue) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model) return env->NewStringUTF("READY");
    const std::string path = jstr(env, pathValue);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return env->NewStringUTF("Could not load model. Check free memory and model file.");
    return env->NewStringUTF("READY");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_codemaster_ai_MainActivity_nativeGenerate(JNIEnv *env, jobject, jstring promptValue) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return env->NewStringUTF("The offline model is not loaded yet.");
    const std::string prompt = jstr(env, promptValue);

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 256;
    cp.n_ubatch = 256;
    cp.n_threads = 4;
    cp.n_threads_batch = 4;
    llama_context *ctx = llama_init_from_model(g_model, cp);
    if (!ctx) return env->NewStringUTF("Could not create the AI context. Close other apps and try again.");

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> tokens(std::max<size_t>(prompt.size() + 32, 512));
    int32_t n_tokens = llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(),
                                      tokens.data(), (int32_t)tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize((size_t)(-n_tokens));
        n_tokens = llama_tokenize(vocab, prompt.c_str(), (int32_t)prompt.size(),
                                  tokens.data(), (int32_t)tokens.size(), true, true);
    }
    if (n_tokens <= 0 || n_tokens > 2000) {
        llama_free(ctx);
        return env->NewStringUTF("Your conversation is too long. Start a new chat and try again.");
    }

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_tokens - 1);
    }
    int decode_result = llama_decode(ctx, batch);
    llama_batch_free(batch);
    if (decode_result != 0) {
        llama_free(ctx);
        return env->NewStringUTF("The model could not process this prompt. Try a shorter message.");
    }

    llama_sampler_chain_params sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.65f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string answer;
    llama_batch next = llama_batch_init(1, 0, 1);
    for (int i = 0; i < 384; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[256];
        int pieceLen = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (pieceLen > 0) answer.append(piece, pieceLen);

        next.n_tokens = 1;
        next.token[0] = token;
        next.pos[0] = n_tokens + i;
        next.n_seq_id[0] = 1;
        next.seq_id[0][0] = 0;
        next.logits[0] = true;
        if (llama_decode(ctx, next) != 0) break;
        llama_sampler_accept(sampler, token);
    }
    llama_batch_free(next);
    llama_sampler_free(sampler);
    llama_free(ctx);
    return env->NewStringUTF(answer.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_codemaster_ai_MainActivity_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        llama_backend_free();
    }
}
