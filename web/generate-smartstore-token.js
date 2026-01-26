/**
 * 스마트스토어 API 액세스 토큰 생성 스크립트
 * 
 * 사용법:
 * node generate-smartstore-token.js
 * 
 * 요구사항:
 * npm install bcryptjs node-fetch
 */

const bcrypt = require('bcryptjs');
const fetch = require('node-fetch');

// 스마트스토어 API 인증 정보
const CLIENT_ID = '40cm5Sgj6LNYjaSiJ1TqL0';
const CLIENT_SECRET = '$2a$04$XdHDByvGUqJD5rn5Pjm7Re';

/**
 * 스마트스토어 액세스 토큰 발급
 * 
 * @returns {Promise<object>} { access_token, expires_in, token_type }
 */
async function generateAccessToken() {
  try {
    console.log('🔐 스마트스토어 토큰 생성 시작...\n');
    
    // 1. 타임스탬프 생성 (현재 시간 - 3초)
    const timestamp = (Date.now() - 3000).toString();
    console.log(`⏰ 타임스탬프: ${timestamp}`);
    
    // 2. password 생성: {CLIENT_ID}_{timestamp}
    const password = `${CLIENT_ID}_${timestamp}`;
    console.log(`🔑 Password: ${password}\n`);
    
    // 3. bcrypt 해싱: bcrypt.hash(password, CLIENT_SECRET)
    console.log('🔄 bcrypt 해싱 중...');
    const hashed = await bcrypt.hash(password, CLIENT_SECRET);
    console.log(`✅ Hashed: ${hashed.substring(0, 50)}...\n`);
    
    // 4. Base64 인코딩
    const clientSecretSign = Buffer.from(hashed).toString('base64');
    console.log(`🔐 Client Secret Sign: ${clientSecretSign.substring(0, 50)}...\n`);
    
    // 5. 토큰 발급 요청
    console.log('📡 토큰 발급 API 호출 중...');
    const response = await fetch(
      'https://api.commerce.naver.com/external/v1/oauth2/token',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
          client_id: CLIENT_ID,
          timestamp: timestamp,
          client_secret_sign: clientSecretSign,
          grant_type: 'client_credentials',
          type: 'SELF',
        }),
      }
    );
    
    // 6. 응답 처리
    const responseText = await response.text();
    
    if (!response.ok) {
      console.error('❌ 토큰 발급 실패:');
      console.error(`   상태 코드: ${response.status}`);
      console.error(`   응답 내용: ${responseText}`);
      throw new Error(`토큰 발급 실패: ${response.status}`);
    }
    
    const data = JSON.parse(responseText);
    
    // 7. 성공 결과 출력
    console.log('\n✅ 토큰 발급 성공!\n');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('📋 토큰 정보:');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log(`Access Token: ${data.access_token}`);
    console.log(`Token Type: ${data.token_type || 'Bearer'}`);
    console.log(`Expires In: ${data.expires_in || 'N/A'} 초`);
    
    if (data.expires_in) {
      const expiryDate = new Date(Date.now() + data.expires_in * 1000);
      console.log(`만료 시간: ${expiryDate.toLocaleString('ko-KR')}`);
    }
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
    
    // 8. 사용 예시
    console.log('💡 사용 예시:');
    console.log('');
    console.log('fetch("https://api.commerce.naver.com/external/v1/...", {');
    console.log('  headers: {');
    console.log(`    "Authorization": "Bearer ${data.access_token}",`);
    console.log('    "Content-Type": "application/json"');
    console.log('  }');
    console.log('});\n');
    
    return data;
    
  } catch (error) {
    console.error('\n❌ 에러 발생:', error.message);
    if (error.stack) {
      console.error('\n스택 트레이스:');
      console.error(error.stack);
    }
    throw error;
  }
}

/**
 * 토큰 검증 (옵션)
 * 발급받은 토큰으로 간단한 API 호출 테스트
 */
async function verifyToken(accessToken) {
  try {
    console.log('🔍 토큰 검증 중...\n');
    
    // 주문 목록 조회로 테스트 (최근 24시간)
    const now = new Date();
    const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
    
    const formatDate = (date) => {
      const offset = 9 * 60 * 60 * 1000; // KST = UTC+9
      const kstDate = new Date(date.getTime() + offset);
      return kstDate.toISOString().replace('Z', '+09:00');
    };
    
    const queryParams = new URLSearchParams({
      rangeType: 'PAYED_DATETIME',
      from: formatDate(yesterday),
      to: formatDate(now),
      pageSize: '1', // 1개만 조회
    });
    
    const apiUrl = `https://api.commerce.naver.com/external/v1/pay-order/seller/product-orders?${queryParams}`;
    
    const response = await fetch(apiUrl, {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
    });
    
    if (!response.ok) {
      console.log(`⚠️  토큰 검증 실패: ${response.status}`);
      const errorText = await response.text();
      console.log(`   응답: ${errorText}`);
      return false;
    }
    
    const data = await response.json();
    console.log('✅ 토큰 검증 성공!');
    console.log(`   API 응답 정상 (주문 수: ${data.data?.contents?.length || 0}건)\n`);
    return true;
    
  } catch (error) {
    console.error('⚠️  토큰 검증 중 에러:', error.message);
    return false;
  }
}

// 메인 실행
(async () => {
  try {
    const tokenData = await generateAccessToken();
    
    // 토큰 검증 (옵션)
    // await verifyToken(tokenData.access_token);
    
  } catch (error) {
    process.exit(1);
  }
})();
